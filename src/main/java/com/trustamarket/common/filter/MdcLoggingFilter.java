package com.trustamarket.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// 요청마다 X-Trace-Id 를 MDC 에 주입하는 서블릿 필터. 헤더가 없으면 새 UUID 를 생성해 응답에 되돌려준다.
// 로그 패턴에 traceId/uri/method 를 포함시켜 요청 단위 로그 상관관계를 확보한다.
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_URI = "uri";
    private static final String MDC_METHOD = "method";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_URI, request.getRequestURI());
        MDC.put(MDC_METHOD, request.getMethod());

        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 풀 재사용 대비 MDC 정리.
            MDC.clear();
        }
    }
}
