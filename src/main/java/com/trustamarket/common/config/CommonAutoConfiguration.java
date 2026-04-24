package com.trustamarket.common.config;

import com.trustamarket.common.config.feign.FeignConfig;
import com.trustamarket.common.config.security.SecurityConfig;
import com.trustamarket.common.config.web.WebConfig;
import com.trustamarket.common.domain.inbox.InboxRepository;
import com.trustamarket.common.domain.outbox.OutboxRepository;
import com.trustamarket.common.event.Events;
import com.trustamarket.common.event.OutboxCallback;
import com.trustamarket.common.event.OutboxDltAckHandler;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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

    // ─── Outbox 계열 (trusta.messaging.outbox.enabled=true 일 때만 활성, default false) ───
    // Kafka + JPA 의존성도 있어야 활성 (@ConditionalOnBean). 프로젝트에서 Outbox 패턴을 실제 쓰는 서비스만 명시적으로 켠다.

    // DLT ack 성공 시 DLT_SENT 전이 전담 — async 콜백의 self-invocation 문제 우회 위해 별도 빈.
    @Bean
    @ConditionalOnProperty(name = "trusta.messaging.outbox.enabled", havingValue = "true")
    @ConditionalOnBean({KafkaTemplate.class, OutboxRepository.class})
    public OutboxDltAckHandler outboxDltAckHandler(OutboxRepository outboxRepository) {
        return new OutboxDltAckHandler(outboxRepository);
    }

    // Kafka 발행 성공/실패 콜백 — PROCESSED/FAILED/DLT_PENDING 상태 전이 + DLT 전송.
    // REQUIRES_NEW 트랜잭션이라 호출자 롤백에 영향받지 않는다.
    @Bean
    @ConditionalOnProperty(name = "trusta.messaging.outbox.enabled", havingValue = "true")
    @ConditionalOnBean({KafkaTemplate.class, OutboxRepository.class})
    public OutboxCallback outboxCallback(OutboxRepository outboxRepository,
                                         KafkaTemplate<String, String> kafkaTemplate,
                                         OutboxDltAckHandler dltAckHandler) {
        return new OutboxCallback(outboxRepository, kafkaTemplate, dltAckHandler);
    }

    // OutboxEvent 수신 → Outbox 테이블 PENDING 저장 → 트랜잭션 커밋 후 Kafka 발행.
    @Bean
    @ConditionalOnProperty(name = "trusta.messaging.outbox.enabled", havingValue = "true")
    @ConditionalOnBean({KafkaTemplate.class, OutboxRepository.class})
    public OutboxEventListener outboxEventListener(OutboxRepository outboxRepository,
                                                   KafkaTemplate<String, String> kafkaTemplate,
                                                   JsonUtil jsonUtil,
                                                   OutboxCallback outboxCallback) {
        return new OutboxEventListener(outboxRepository, kafkaTemplate, jsonUtil, outboxCallback);
    }

    // 10초 주기로 PENDING/FAILED 는 원 토픽, DLT_PENDING 은 DLT 토픽으로 재발행.
    // 상태 전이는 OutboxCallback / OutboxDltAckHandler 에 위임해 REQUIRES_NEW 로 격리.
    @Bean
    @ConditionalOnProperty(name = "trusta.messaging.outbox.enabled", havingValue = "true")
    @ConditionalOnBean({KafkaTemplate.class, OutboxRepository.class})
    public OutboxRelayScheduler outboxRelayScheduler(OutboxRepository outboxRepository,
                                                     KafkaTemplate<String, String> kafkaTemplate,
                                                     OutboxCallback outboxCallback,
                                                     OutboxDltAckHandler dltAckHandler) {
        return new OutboxRelayScheduler(outboxRepository, kafkaTemplate, outboxCallback, dltAckHandler);
    }

    // ─── Inbox 계열 (trusta.messaging.inbox.enabled=true 일 때만 활성, default false) ───
    // InboxRepository (JPA) 가 있어야 활성. 멱등 소비가 필요한 서비스만 명시적으로 켠다.

    // @IdempotentConsumer AOP. message_id 헤더로 중복 소비 방지.
    @Bean
    @ConditionalOnProperty(name = "trusta.messaging.inbox.enabled", havingValue = "true")
    @ConditionalOnBean(InboxRepository.class)
    public InboxAdvice inboxAdvice(InboxRepository inboxRepository) {
        return new InboxAdvice(inboxRepository);
    }

    // 매일 03:00 (UTC) 에 7일 이전 Inbox 삭제. 테이블 비대화 방지.
    @Bean
    @ConditionalOnProperty(name = "trusta.messaging.inbox.enabled", havingValue = "true")
    @ConditionalOnBean(InboxRepository.class)
    public InboxCleanupScheduler inboxCleanupScheduler(InboxRepository inboxRepository) {
        return new InboxCleanupScheduler(inboxRepository);
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
