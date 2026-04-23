package com.trustamarket.common.event;

import com.trustamarket.common.domain.outbox.Outbox;
import com.trustamarket.common.domain.outbox.OutboxRepository;
import com.trustamarket.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;

// OutboxEvent 를 받아 트랜잭션 안에선 Outbox 로 저장, 커밋 후엔 Kafka 로 발행하는 이벤트 리스너.
// 트랜잭션 롤백 시 Outbox 저장 자체가 없던 일이 되므로 "커밋된 이벤트만 발행" 이 보장된다.
@Slf4j
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;
    private final OutboxCallback outboxCallback;

    // 도메인 트랜잭션에 편승해 Outbox 행을 저장. correlationId unique 제약에 의존해 중복 삽입을 DB 레벨에서 차단.
    // 사전 조회 + 저장 2단계는 TOCTOU race 가 있어, saveAndFlush 후 DataIntegrityViolationException 을 catch 하는 방식으로 대체.
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordOutbox(OutboxEvent event) {
        Outbox outbox = Outbox.builder()
                .correlationId(event.correlationId())
                .domainType(event.domainType())
                .domainId(event.domainId())
                .eventType(event.eventType())
                .payload(jsonUtil.toJson(event.payload()))
                .build();

        try {
            outboxRepository.saveAndFlush(outbox);
            log.debug("Outbox 저장: correlationId={}, eventType={}", event.correlationId(), event.eventType());
        } catch (DataIntegrityViolationException e) {
            // 동일 correlationId 가 이미 있음 — idempotent skip.
            log.warn("중복 correlationId 무시: {}", event.correlationId());
        }
    }

    // AFTER_COMMIT 에만 Kafka 로 발행. 헤더에 message_id/correlation_id 를 심어 소비 측 Inbox 멱등성에 사용.
    // 전송 콜백은 OutboxCallback 이 별도 트랜잭션(REQUIRES_NEW)에서 상태 전이를 처리.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(OutboxEvent event) {
        outboxRepository.findByCorrelationId(event.correlationId()).ifPresent(outbox -> {
            // domainId nullable 가드 — 파티션 키로 쓰므로 null 이면 라운드로빈 배치.
            String key = outbox.getDomainId() != null ? outbox.getDomainId().toString() : null;
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    outbox.getEventType(),
                    key,
                    outbox.getPayload());
            record.headers().add("message_id", outbox.getId().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("correlation_id", outbox.getCorrelationId().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            outboxCallback.onSuccess(outbox.getCorrelationId());
                        } else {
                            outboxCallback.onFailure(outbox.getCorrelationId(), ex);
                        }
                    });
        });
    }
}
