package com.trustamarket.common.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Gateway 가 주입한 X-User-* 헤더 → SecurityContext 주입.
// UUID/Email 중 하나라도 없으면 익명 요청으로 통과. Enabled=false 면 DisabledException.
// 필터 예외는 handlerExceptionResolver 로 위임되어 GlobalExceptionAdvice 가 ErrorResponse 로 변환.
// trustGatewayHeaders=false (default) 이면 전체 스킵 — X-User-* 헤더를 신뢰하지 않음.
// Gateway 가 strip+re-issue 해주는 환경에서만 true 로 명시 opt-in 해야 header spoofing 방지.
@Slf4j
public class LoginFilter extends OncePerRequestFilter {

    // Gateway 와의 헤더 계약. 이름 변경 시 Gateway 쪽도 동시 수정 필요.
    private static final String HEADER_USER_UUID = "X-User-UUID";
    private static final String HEADER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_ROLE = "X-User-Role";
    private static final String HEADER_SLACK_ID = "X-User-Slack-Id";
    private static final String HEADER_ENABLED = "X-User-Enabled";

    // Spring MVC 예외 해석기. 필터 예외를 @RestControllerAdvice 로 흘려보낸다.
    private final HandlerExceptionResolver resolver;

    // trusta.security.trust-gateway-headers 프로퍼티. 소비 서비스가 명시 opt-in 해야 활성.
    private final boolean trustGatewayHeaders;

    public LoginFilter(HandlerExceptionResolver resolver, boolean trustGatewayHeaders) {
        this.resolver = resolver;
        this.trustGatewayHeaders = trustGatewayHeaders;
        if (trustGatewayHeaders) {
            // trust boundary 명시 — 배포/설정 실수로 Gateway 뒤가 아닌 곳에 서비스가 노출되면 spoofing 가능.
            log.warn("[LoginFilter] Gateway 헤더 신뢰 모드 활성 — 서비스는 반드시 Gateway 뒤에만 배치되어야 하며, Gateway 가 X-User-* 를 strip+re-issue 해야 합니다.");
        } else {
            log.info("[LoginFilter] Gateway 헤더 신뢰 모드 비활성 — 헤더 기반 인증 스킵. 활성하려면 trusta.security.trust-gateway-headers=true 설정.");
        }
    }

    // 헤더 기반 인증 시도. 인증/검증 실패 시 체인을 끊고 예외 응답으로 종료.
    // clearContext 는 doLogin 내부에서 새 authentication 세팅 직전에만 호출 — 헤더 없으면 기존 context 존중.
    // trustGatewayHeaders=false 면 doLogin 전체 스킵 (header spoofing 방지 — 명시 opt-in 필요).
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!trustGatewayHeaders) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            doLogin(request);
        } catch (DisabledException e) {
            log.warn("[LoginFilter] 접근 제한: {}", e.getMessage());
            resolver.resolveException(request, response, null, e);
            return;
        } catch (Exception e) {
            log.error("SecurityContext 설정 실패", e);
            resolver.resolveException(request, response, null, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    // 헤더에서 유저 정보를 추출하여 SecurityContext 에 Authentication 을 세팅.
    private void doLogin(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_UUID);
        String email = request.getHeader(HEADER_EMAIL);

        // UUID/Email 둘 중 하나라도 비면 비인증 요청으로 간주 — 컨텍스트 미설정 후 체인 진행.
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(email)) {
            return;
        }

        String name = request.getHeader(HEADER_USER_NAME);
        String roles = request.getHeader(HEADER_ROLE);
        String slackId = request.getHeader(HEADER_SLACK_ID);
        String enabled = request.getHeader(HEADER_ENABLED);

        // name 은 한글 등 때문에 Gateway 가 URL 인코딩.
        if (StringUtils.hasText(name)) {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        }

        // enabled 누락은 Gateway 구성 오류 가능성이 크므로 즉시 실패.
        if (!StringUtils.hasText(enabled)) {
            throw new BadCredentialsException("X-User-Enabled 헤더가 누락되었습니다.");
        }

        try {
            UserDetails userDetails = UserDetailsImpl.builder()
                    .uuid(UUID.fromString(userId.trim()))
                    .email(email)
                    .name(name)
                    .slackId(slackId)
                    .roles(roles)
                    .enabled("true".equalsIgnoreCase(enabled))
                    .build();

            // 탈퇴/승인 대기 유저 차단.
            if (!userDetails.isEnabled()) {
                throw new DisabledException("승인 대기 중이거나 탈퇴한 사용자입니다.");
            }

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // 새 인증 세팅 직전에만 기존 context 를 비운다 — 다른 인증 필터와 공존 가능하도록.
            SecurityContextHolder.clearContext();
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (IllegalArgumentException e) {
            // UUID.fromString 실패 등.
            throw new BadCredentialsException("잘못된 인증 헤더 형식입니다.", e);
        }
    }
}
