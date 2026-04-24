package com.trustamarket.common.util;

import com.trustamarket.common.config.security.UserDetailsImpl;
import com.trustamarket.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

// SecurityContext 에서 현재 사용자 정보를 꺼내는 정적 헬퍼.
// LoginFilter 가 X-User-* 헤더로 주입한 UserDetailsImpl 을 반환한다.
public class SecurityUtil {

    private SecurityUtil() {}

    public static Optional<UserDetailsImpl> getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(principal -> principal instanceof UserDetailsImpl)
                .map(UserDetailsImpl.class::cast);
    }

    public static Optional<UUID> getCurrentUserId() {
        return getCurrentUser().map(UserDetailsImpl::getUuid);
    }

    // 인증되지 않은 상태면 UnauthorizedException(401) 발생. 도메인 서비스에서 userId 가 필수인 경로에 사용.
    public static UUID getCurrentUserIdOrThrow() {
        return getCurrentUserId()
                .orElseThrow(UnauthorizedException::new);
    }

    public static Optional<String> getCurrentUsername() {
        return getCurrentUser().map(UserDetailsImpl::getUsername);
    }
}
