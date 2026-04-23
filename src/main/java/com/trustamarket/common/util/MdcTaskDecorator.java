package com.trustamarket.common.util;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

// @Async 스레드에 부모 스레드의 MDC(traceId 등)를 전파하는 TaskDecorator.
// EventConfig 의 ThreadPool 에 설정되어 비동기 로그에도 traceId 가 유지된다.
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
