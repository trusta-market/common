package com.trustamarket.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// 요청마다 X-Trace-Id 를 MDC 에 주입하는 서블릿 필터. 헤더가 없거나 형식 부적합이면 새 UUID 를 생성해 응답에 되돌려준다.
// 로그 패턴에 traceId/uri/method 를 포함시켜 요청 단위 로그 상관관계를 확보한다.
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_URI = "uri";
    private static final String MDC_METHOD = "method";

    // traceId 허용 문자: 영숫자 + . _ - 만. 최대 128자.
    // 외부 입력이 그대로 MDC 로그 / 응답 헤더에 들어가므로 log forging (개행 등 제어문자) / 헤더 인젝션 방지.
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 헤더가 비었거나 허용 패턴을 벗어나면 UUID 로 재생성 — 악성 입력이 로그/응답에 그대로 새지 않도록.
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && TRACE_ID_PATTERN.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

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
