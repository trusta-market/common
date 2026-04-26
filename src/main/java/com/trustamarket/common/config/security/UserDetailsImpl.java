package com.trustamarket.common.config.security;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// SecurityContext 에 들어가는 유저 표현. Gateway 가 준 X-User-* 헤더 값을 담는다.
// SecurityUtil 로 꺼내 쓸 수 있으며, roles 는 콤마 구분 문자열을 GrantedAuthority 로 변환.
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class UserDetailsImpl implements UserDetails {

    // ToString.Include 필드는 로그에서 식별이 필요한 최소 정보만 — PII 보호 목적.
    @ToString.Include
    private final UUID uuid;
    private final String email;
    private final String name;
    private final String slackId;
    @ToString.Include
    private final String roles;
    @ToString.Include
    private final boolean enabled;

    @Builder
    public UserDetailsImpl(UUID uuid, String email, String name,
                           String slackId, String roles, boolean enabled) {
        this.uuid = uuid;
        this.email = email;
        this.name = name;
        this.slackId = slackId;
        this.roles = roles;
        this.enabled = enabled;
    }

    // 콤마 구분 roles 문자열(예: "ROLE_ADMIN,ROLE_USER")을 GrantedAuthority 로 변환.
    // 비어 있으면 빈 리스트.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (!StringUtils.hasText(roles)) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    // Spring Security 의 Username 위치에 이메일을 노출 — 로그/감사용.
    @Override
    public String getUsername() {
        return email;
    }

    // 비밀번호 없이 동작 (Gateway 가 이미 JWT 검증 완료).
    @Override
    public String getPassword() {
        return "";
    }

    // 계정 상태 플래그. enabled 만 실제로 사용하고 나머지는 항상 true.
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
