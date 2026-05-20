package com.trustamarket.common.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Spring Security 공통 설정.
// Gateway 가 JWT 검증 후 X-User-* 헤더를 주입 → LoginFilter 가 SecurityContext 에 세팅.
// 세션은 stateless, URL 단위 권한은 @PreAuthorize 로 메서드 단위 위임.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final LoginFilter loginFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    // CSRF off (REST API), stateless 세션, LoginFilter 앞에 배치, 401/403 은 ErrorResponse JSON.
    // Swagger 경로 permit 은 trusta.swagger.expose-public=true 일 때만 — 라이브러리 default 는 닫혀있음.
    // 소비 서비스가 application.yaml 에 명시적으로 opt-in 해야 활성화 (외부 직접 노출 방지).
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            @Value("${trusta.swagger.expose-public:false}") boolean exposeSwaggerPublic
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class)
                // deny by default — actuator health/info 외 모든 요청은 인증 필수.
                // 소비 서비스의 public 엔드포인트(/signup, /login 등)는 각자 SecurityConfig 에서 override.
                .authorizeHttpRequests(authorize -> {
                    // K8s probe (liveness/readiness) 와 Prometheus scrape 가 인증 없이 접근.
                    // /actuator/health/** 와일드카드로 /actuator/health/liveness, /readiness 등 포함.
                    authorize.requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll();
                    // Swagger / OpenAPI 경로 — 소비 서비스가 trusta.swagger.expose-public=true 명시 시에만 permit.
                    // 외부 노출 보호는 api-gateway 에서 ADMIN role 로 (서비스 직접 노출 환경에선 false 유지).
                    if (exposeSwaggerPublic) {
                        authorize.requestMatchers(
                                "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-resources/**", "/webjars/**"
                        ).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(c -> {
                    c.authenticationEntryPoint(authenticationEntryPoint);
                    c.accessDeniedHandler(accessDeniedHandler);
                });

        return http.build();
    }
}
