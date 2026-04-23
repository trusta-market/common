package com.trustamarket.common.config.feign;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

// Feign 공통 설정. com.trustamarket 하위 @FeignClient 자동 스캔.
// 현재 요청의 인증/추적 헤더를 아웃바운드 호출에 자동 전파한다.
// 도메인 서비스는 별도 @EnableFeignClients 를 붙이면 안 된다 (중복 스캔으로 빈 충돌).
@Configuration
@EnableFeignClients(basePackages = "com.trustamarket")
public class FeignConfig {

    // 아웃바운드 Feign 요청으로 복사할 헤더. 헤더 추가 시 이 목록만 수정.
    private static final List<String> PROPAGATE_HEADERS = List.of(
            "Authorization",
            "X-User-UUID",
            "X-User-Email",
            "X-User-Name",
            "X-User-Role",
            "X-User-Slack-Id",
            "X-User-Enabled",
            "X-Trace-Id"
    );

    // 현재 HTTP 요청의 헤더를 Feign 템플릿에 복사.
    // 비동기/스케줄러 컨텍스트에는 RequestAttributes 가 없어 조용히 skip 된다.
    @Bean
    public RequestInterceptor requestHeaderInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return;

            HttpServletRequest request = attributes.getRequest();
            for (String header : PROPAGATE_HEADERS) {
                String value = request.getHeader(header);
                if (value != null) {
                    template.header(header, value);
                }
            }
        };
    }
}
