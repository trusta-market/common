package com.trustamarket.common.util;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

// @Async / @TransactionalEventListener 스레드에 부모 스레드의 컨텍스트를 전파한다.
//
// 전파 대상:
// - MDC (X-Trace-Id 등)            — 비동기 로그에도 traceId 유지
// - RequestAttributes (HTTP 요청)   — FeignConfig 가 X-User-* 헤더 전파에 사용
//                                     (RequestContextHolder 는 ThreadLocal 기반이라 async 면 null)
// - SecurityContext                — @PreAuthorize 등 권한 검사가 async 흐름에서도 동작
//
// EventConfig.taskExecutor 에 설정되어 적용된다. 이름은 호환 유지 — 내부적으로 컨텍스트 3종 전파.
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        SecurityContext securityContext = SecurityContextHolder.getContext();

        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                SecurityContextHolder.setContext(securityContext);
                runnable.run();
            } finally {
                MDC.clear();
                RequestContextHolder.resetRequestAttributes();
                SecurityContextHolder.clearContext();
            }
        };
    }
}
