package com.trustamarket.common.config.web;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

// Pageable 파라미터의 size 를 Trusta 정책(10/30/50) 으로 강제 제한.
// 그 외 값이나 미지정 시 10 으로 고정. page/sort 는 사용자 입력 그대로 유지.
public class RestrictedPageableResolver extends PageableHandlerMethodArgumentResolver {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);
    private static final int DEFAULT_SIZE = 10;

    // size 파라미터가 아예 없을 때의 fallback.
    public RestrictedPageableResolver() {
        setFallbackPageable(PageRequest.of(0, DEFAULT_SIZE));
    }

    // 부모가 파싱한 Pageable 에서 size 만 허용 목록으로 검증 후 덮어쓴다.
    @Override
    public Pageable resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Pageable pageable = super.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        int size = ALLOWED_SIZES.contains(pageable.getPageSize()) ? pageable.getPageSize() : DEFAULT_SIZE;
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}
