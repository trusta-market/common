package com.trustamarket.common.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustamarket.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

// 인증은 됐지만 권한이 없는 요청(403)에 대한 핸들러.
// Spring Security 기본 동작(빈 403 응답) 대신 RFC 9457 ErrorResponse JSON 으로 응답.
@Slf4j
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    // 403 응답 바디를 ErrorResponse 로 직렬화해 작성. status/contentType 도 직접 세팅.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[{}] Access Denied: {}", request.getRequestURI(), accessDeniedException.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "접근 권한이 없습니다.",
                request.getRequestURI());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
