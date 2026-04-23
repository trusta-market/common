package com.trustamarket.common.config;

import com.trustamarket.common.config.feign.FeignConfig;
import com.trustamarket.common.config.security.SecurityConfig;
import com.trustamarket.common.config.web.WebConfig;
import com.trustamarket.common.domain.inbox.InboxRepository;
import com.trustamarket.common.domain.outbox.OutboxRepository;
import com.trustamarket.common.event.Events;
import com.trustamarket.common.event.OutboxCallback;
import com.trustamarket.common.event.OutboxEventListener;
import com.trustamarket.common.event.OutboxRelayScheduler;
import com.trustamarket.common.exception.GlobalExceptionAdvice;
import com.trustamarket.common.config.security.CustomAccessDeniedHandler;
import com.trustamarket.common.config.security.CustomAuthenticationEntryPoint;
import com.trustamarket.common.config.security.LoginFilter;
import com.trustamarket.common.filter.MdcLoggingFilter;
import com.trustamarket.common.messaging.InboxAdvice;
import com.trustamarket.common.messaging.InboxCleanupScheduler;
import com.trustamarket.common.response.CommonResponseAdvice;
import com.trustamarket.common.util.JsonUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
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

    // 컨트롤러 반환값을 CommonResponse / PagedResponse 로 자동 래핑.
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

    // Events.trigger(OutboxEvent) 의 내부 헬퍼. Spring 이벤트를 통해 Outbox 리스너로 넘긴다.
    @Bean
    public Events events(ApplicationEventPublisher publisher) {
        return new Events(publisher);
    }

    // Kafka 발행 성공/실패 콜백 — PROCESSED/FAILED 상태 업데이트 + DLT 전송.
    // REQUIRES_NEW 트랜잭션이라 호출자 롤백에 영향받지 않는다.
    // KafkaTemplate 이 컨텍스트에 있을 때만 등록 (Kafka 미사용 서비스에서는 건너뜀).
    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public OutboxCallback outboxCallback(OutboxRepository outboxRepository,
                                         KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxCallback(outboxRepository, kafkaTemplate);
    }

    // OutboxEvent 수신 → Outbox 테이블 PENDING 저장 → 트랜잭션 커밋 후 Kafka 발행.
    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public OutboxEventListener outboxEventListener(OutboxRepository outboxRepository,
                                                   KafkaTemplate<String, String> kafkaTemplate,
                                                   JsonUtil jsonUtil,
                                                   OutboxCallback outboxCallback) {
        return new OutboxEventListener(outboxRepository, kafkaTemplate, jsonUtil, outboxCallback);
    }

    // 10초 주기로 PENDING/FAILED 메시지를 재발행. 네트워크 장애 복구용.
    // 엔트리별 상태 전이를 위해 OutboxCallback 을 주입받아 재사용 — DLT 로직 단일화.
    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public OutboxRelayScheduler outboxRelayScheduler(OutboxRepository outboxRepository,
                                                     KafkaTemplate<String, String> kafkaTemplate,
                                                     OutboxCallback outboxCallback) {
        return new OutboxRelayScheduler(outboxRepository, kafkaTemplate, outboxCallback);
    }

    // @IdempotentConsumer AOP. message_id 헤더로 중복 소비 방지.
    // InboxRepository (JPA) 가 있을 때만 등록.
    @Bean
    @ConditionalOnBean(InboxRepository.class)
    public InboxAdvice inboxAdvice(InboxRepository inboxRepository) {
        return new InboxAdvice(inboxRepository);
    }

    // 매일 03:00 에 7일 이전 Inbox 삭제. 테이블 비대화 방지.
    @Bean
    @ConditionalOnBean(InboxRepository.class)
    public InboxCleanupScheduler inboxCleanupScheduler(InboxRepository inboxRepository) {
        return new InboxCleanupScheduler(inboxRepository);
    }

    // Gateway 의 X-User-* 헤더 → SecurityContext.
    // 필터 내부 예외는 handlerExceptionResolver 로 위임되어 GlobalExceptionAdvice 가 처리.
    @Bean
    public LoginFilter loginFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new LoginFilter(resolver);
    }

    // 401 응답 핸들러. ErrorResponse JSON 으로 응답하므로 ObjectMapper 주입.
    @Bean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new CustomAuthenticationEntryPoint(objectMapper);
    }

    // 403 응답 핸들러.
    @Bean
    public CustomAccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
        return new CustomAccessDeniedHandler(objectMapper);
    }
}
