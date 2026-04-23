package com.trustamarket.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// 컨트롤러 반환값을 CommonResponse / PagedResponse 로 자동 래핑하는 Advice.
// String 반환은 제외(컨버터 이슈), 이미 래핑된 응답은 통과, Page<?> 는 PagedResponse 로 전환, 그 외는 CommonResponse 로 감싼다.
public class CommonResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> paramType = returnType.getParameterType();
        return !paramType.equals(String.class)
                && !paramType.equals(CommonResponse.class)
                && !paramType.equals(PagedResponse.class)
                && !paramType.equals(ErrorResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof CommonResponse || body instanceof PagedResponse || body instanceof ErrorResponse) {
            return body;
        }

        int status = resolveStatus(response);

        if (body instanceof Page<?> page) {
            return wrapPage(status, page);
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
}
