package com.trustamarket.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

// @Async / @TransactionalEventListener 스레드에 부모 스레드의 컨텍스트를 전파한다.
//
// 전파 대상:
// - MDC (X-Trace-Id 등)           — 비동기 로그에도 traceId 유지
// - 헤더 snapshot (X-User-* 등)   — FeignConfig 가 async 흐름에서도 헤더 전파
//                                    HttpServletRequest 참조 직접 전달 시 라이프사이클 종료 후
//                                    IllegalStateException 위험 → snapshot Map 으로 캡처
// - SecurityContext (복사본)      — @PreAuthorize 등 권한 검사 동작
//                                    참조 공유 시 부모/자식 race condition → createEmptyContext 로 복사
//
// EventConfig.taskExecutor 에 설정되어 적용된다.
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        Map<String, String> headerSnapshot = captureHeaderSnapshot();
        SecurityContext securityContextCopy = copySecurityContext();

        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                if (headerSnapshot != null) {
                    HeaderSnapshotHolder.set(headerSnapshot);
                }
                SecurityContextHolder.setContext(securityContextCopy);
                runnable.run();
            } finally {
                MDC.clear();
                HeaderSnapshotHolder.clear();
                SecurityContextHolder.clearContext();
            }
        };
    }

    // 부모 thread 의 HttpServletRequest 에서 전파 대상 헤더만 추출.
    // RequestContextHolder 가 ServletRequestAttributes 가 아니면 (e.g. 스케줄러) null 반환.
    private Map<String, String> captureHeaderSnapshot() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Map<String, String> snapshot = new HashMap<>(HeaderSnapshotHolder.PROPAGATE_HEADERS.size() * 2);
        for (String header : HeaderSnapshotHolder.PROPAGATE_HEADERS) {
            String value = request.getHeader(header);
            if (value != null) {
                snapshot.put(header, value);
            }
        }
        return snapshot;
    }

    // SecurityContext 를 새로 만들어 Authentication 만 복사. 참조 공유로 인한 race condition 방지.
    // Spring 의 DelegatingSecurityContextRunnable 과 동일 패턴.
    private SecurityContext copySecurityContext() {
        SecurityContext original = SecurityContextHolder.getContext();
        SecurityContext copy = SecurityContextHolder.createEmptyContext();
        Authentication auth = original.getAuthentication();
        if (auth != null) {
            copy.setAuthentication(auth);
        }
        return copy;
    }
}
