package com.trustamarket.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustamarket.common.config.feign.FeignConfig;
import com.trustamarket.common.config.security.CustomAccessDeniedHandler;
import com.trustamarket.common.config.security.CustomAuthenticationEntryPoint;
import com.trustamarket.common.config.security.LoginFilter;
import com.trustamarket.common.config.security.SecurityConfig;
import com.trustamarket.common.config.web.WebConfig;
import com.trustamarket.common.event.Events;
import com.trustamarket.common.exception.GlobalExceptionAdvice;
import com.trustamarket.common.filter.MdcLoggingFilter;
import com.trustamarket.common.response.CommonResponseAdvice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

// Trusta common 의 Spring Boot 자동 설정 진입점.
// 의존성만 추가하면 AutoConfiguration.imports 를 통해 여기가 로드되어
// 아래 Config 들과 @Bean 컴포넌트가 일괄 등록된다.
// @ComponentScan 대신 명시적 @Import + @Bean 방식으로 등록 대상이 예측 가능하다.
@AutoConfiguration
@Import({
        JpaConfig.class,
        JsonConfig.class,
        EventConfig.class,
        FeignConfig.class,
        WebConfig.class,
        SecurityConfig.class
})
public class CommonAutoConfiguration {

    // 전역 예외 핸들러. 모든 예외를 RFC 9457 ErrorResponse 로 변환.
    // 소비 서비스가 자체 advice 를 등록하면 그쪽이 우선.
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionAdvice globalExceptionAdvice() {
        return new GlobalExceptionAdvice();
    }

    // 컨트롤러 반환값을 CommonResponse / PagedResponse / SlicedResponse 로 자동 래핑.
    // 소비 서비스가 자체 advice 를 등록하면 그쪽이 우선.
    @Bean
    @ConditionalOnMissingBean
    public CommonResponseAdvice commonResponseAdvice() {
        return new CommonResponseAdvice();
    }

    // X-Trace-Id 를 MDC 에 주입하는 필터. 가장 앞에서 실행되어야 모든 로그가 traceId 를 갖는다.
    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter() {
        FilterRegistrationBean<MdcLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // Events.trigger(OutboxEvent) 의 정적 게이트웨이. 도메인이 주입 없이 ApplicationEvent 를 발행하도록 돕는 얇은 헬퍼.
    // 실제 이벤트 수신/Kafka 발행/상태 전이 등 인프라 로직은 common 이 아닌 각 도메인 서비스에서 구현한다.
    @Bean
    public Events events(ApplicationEventPublisher publisher) {
        return new Events(publisher);
    }

    // ─── Security 빈 (소비자 override 허용) ───

    // Gateway 의 X-User-* 헤더 → SecurityContext.
    // trust-gateway-headers 프로퍼티가 명시적으로 true 일 때만 활성 (default false, header spoofing 방지).
    // 필터 내부 예외는 handlerExceptionResolver 로 위임되어 GlobalExceptionAdvice 가 처리.
    // 소비 서비스가 자체 LoginFilter 를 등록하면 그쪽이 우선.
    @Bean
    @ConditionalOnMissingBean
    public LoginFilter loginFilter(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            @Value("${trusta.security.trust-gateway-headers:false}") boolean trustGatewayHeaders) {
        return new LoginFilter(resolver, trustGatewayHeaders);
    }

    // 401 응답 핸들러. ErrorResponse JSON 으로 응답하므로 ObjectMapper 주입.
    // 소비 서비스가 자체 EntryPoint 를 등록하면 그쪽이 우선.
    @Bean
    @ConditionalOnMissingBean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new CustomAuthenticationEntryPoint(objectMapper);
    }

    // 403 응답 핸들러. 소비 서비스가 자체 AccessDeniedHandler 를 등록하면 그쪽이 우선.
    @Bean
    @ConditionalOnMissingBean
    public CustomAccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
        return new CustomAccessDeniedHandler(objectMapper);
    }
}
