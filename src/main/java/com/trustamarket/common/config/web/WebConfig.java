package com.trustamarket.common.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// Spring MVC 공통 설정.
// Pageable 파라미터에 size 제한(10/30/50)을 적용하기 위해 RestrictedPageableResolver 를 등록.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 기본 Pageable 해석기보다 먼저 우리 resolver 가 동작하도록 체인 앞쪽에 추가.
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(0, new RestrictedPageableResolver());
    }
}
