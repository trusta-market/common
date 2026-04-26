package com.trustamarket.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// 컨트롤러 반환값을 CommonResponse / PagedResponse / SlicedResponse 로 자동 래핑하는 Advice.
// @RestControllerAdvice 로 Spring 이 ResponseBodyAdvice 로 자동 인식하도록 한다 (@Bean 등록만으론 부족).
// String 반환은 제외(컨버터 이슈), 이미 래핑된 응답은 통과.
// Page<?> → PagedResponse, Slice<?> (Page 아닌) → SlicedResponse, 그 외 → CommonResponse.
@RestControllerAdvice
public class CommonResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> paramType = returnType.getParameterType();
        // ResponseEntity<String> / HttpEntity<String> 도 String 으로 간주 — StringHttpMessageConverter 가 선택되므로 wrap 하면 변환 실패.
        if (String.class.equals(paramType)) {
            return false;
        }
        if (HttpEntity.class.isAssignableFrom(paramType)) {
            Class<?> generic = ResolvableType.forMethodParameter(returnType).getGeneric(0).resolve();
            if (generic != null && String.class.equals(generic)) {
                return false;
            }
        }
        return !paramType.equals(CommonResponse.class)
                && !paramType.equals(PagedResponse.class)
                && !paramType.equals(SlicedResponse.class)
                && !paramType.equals(ErrorResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // null body 는 래핑 금지 — HTTP 204/304 zero-byte 응답 계약을 깨지 않도록.
        if (body == null) {
            return null;
        }

        int status = resolveStatus(response);
        // 204 No Content / 304 Not Modified 은 본문이 없어야 하므로 래핑하지 않고 통과.
        if (status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.NOT_MODIFIED.value()) {
            return body;
        }

        if (body instanceof CommonResponse
                || body instanceof PagedResponse
                || body instanceof SlicedResponse
                || body instanceof ErrorResponse) {
            return body;
        }

        // Page 는 Slice 의 서브타입이라 Page 체크가 먼저 와야 한다.
        if (body instanceof Page<?> page) {
            return wrapPage(status, page);
        }
        if (body instanceof Slice<?> slice) {
            return wrapSlice(status, slice);
        }
        return CommonResponse.of(status, body);
    }

    // ServletServerHttpResponse 에서 현재 status 를 꺼낸다. 0 또는 미확정이면 200 으로 fallback.
    private int resolveStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            int raw = servletResponse.getServletResponse().getStatus();
            return raw > 0 ? raw : HttpStatus.OK.value();
        }
        return HttpStatus.OK.value();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private PagedResponse<?> wrapPage(int status, Page<?> page) {
        return PagedResponse.of(status, (Page) page);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SlicedResponse<?> wrapSlice(int status, Slice<?> slice) {
        return SlicedResponse.of(status, (Slice) slice);
    }
}
