package com.trustamarket.common.config.feign;

import com.trustamarket.common.util.HeaderSnapshotHolder;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

// Feign 공통 설정. com.trustamarket 하위 @FeignClient 자동 스캔.
// 현재 요청의 인증/추적 헤더를 아웃바운드 호출에 자동 전파한다.
// 도메인 서비스는 별도 @EnableFeignClients 를 붙이면 안 된다 (중복 스캔으로 빈 충돌).
@Configuration
@EnableFeignClients(basePackages = "com.trustamarket")
public class FeignConfig {

    // 헤더 전파 우선순위:
    // 1. HeaderSnapshotHolder (async thread — MdcTaskDecorator 가 미리 캡처해 둔 snapshot)
    // 2. RequestContextHolder (동기 thread — 원본 HttpServletRequest)
    //
    // 전파 대상 헤더 목록은 HeaderSnapshotHolder.PROPAGATE_HEADERS 에 중앙화 (헤더 추가 시 한 곳만 수정).
    @Bean
    public RequestInterceptor requestHeaderInterceptor() {
        return template -> {
            Map<String, String> snapshot = HeaderSnapshotHolder.get();
            if (snapshot != null) {
                snapshot.forEach(template::header);
                return;
            }

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return;

            HttpServletRequest request = attributes.getRequest();
            for (String header : HeaderSnapshotHolder.PROPAGATE_HEADERS) {
                String value = request.getHeader(header);
                if (value != null) {
                    template.header(header, value);
                }
            }
        };
    }
}
