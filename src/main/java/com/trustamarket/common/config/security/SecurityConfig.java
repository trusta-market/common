package com.trustamarket.common.config.security;

import lombok.RequiredArgsConstructor;
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
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class)
                // deny by default — actuator health/info 외 모든 요청은 인증 필수.
                // 소비 서비스의 public 엔드포인트(/signup, /login 등)는 각자 SecurityConfig 에서 override.
                .authorizeHttpRequests(authorize -> authorize
                        // K8s probe (liveness/readiness) 와 Prometheus scrape 가 인증 없이 접근.
                        // /actuator/health/** 와일드카드로 /actuator/health/liveness, /readiness 등 포함.
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Swagger / OpenAPI — 외부 노출은 api-gateway 에서 ADMIN role 로 보호.
                        // 각 서비스의 /v3/api-docs/** 는 api-gateway 가 cluster 내부에서 fetch (aggregation).
                        // /swagger-ui/** 는 서비스 직접 접근 시 dev 디버깅 용도.
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(c -> {
                    c.authenticationEntryPoint(authenticationEntryPoint);
                    c.accessDeniedHandler(accessDeniedHandler);
                });

        return http.build();
    }
}
