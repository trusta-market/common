package com.trustamarket.common.event;

import com.trustamarket.common.domain.outbox.Outbox;
import com.trustamarket.common.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

// Kafka 전송 결과에 따라 Outbox 상태를 전이하는 콜백.
// REQUIRES_NEW 로 독립 트랜잭션에서 실행해 도메인 트랜잭션과 분리한다.
// OutboxEventListener / OutboxRelayScheduler 가 공통으로 사용 — DLT 로직 단일화.
@Slf4j
@RequiredArgsConstructor
public class OutboxCallback {

    // 재시도 상한. 초과하면 DLT 로 격리.
    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 발행 성공 시 PROCESSED 로 전이.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSuccess(String correlationId) {
        outboxRepository.findByCorrelationId(correlationId).ifPresent(outbox -> {
            outbox.complete();
            outboxRepository.save(outbox);
            log.info("Kafka 발행 성공: correlationId={}, topic={}", correlationId, outbox.getEventType());
        });
    }

    // 실패 시 FAILED + retryCount++. 3회 초과면 DLT 로 격리하고, 그 외엔 OutboxRelayScheduler 가 재시도.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailure(String correlationId, Throwable ex) {
        outboxRepository.findByCorrelationId(correlationId).ifPresent(outbox -> {
            outbox.fail();
            outboxRepository.saveAndFlush(outbox);

            if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                log.error("최대 재시도 횟수 초과({}회). DLT로 격리: correlationId={}",
                        outbox.getRetryCount(), correlationId);
                sendToDlt(outbox);
            } else {
                log.warn("Kafka 발행 실패 (재시도 예정 {}/{}): correlationId={}, error={}",
                        outbox.getRetryCount(), MAX_RETRY_COUNT, correlationId, ex.getMessage());
            }
        });
    }

    // 원 토픽명 + ".DLT" 로 보내 모니터링/수동 복구 대상으로 남긴다.
    // message_id / correlation_id 헤더를 포함해 DLT 에서도 소비자가 Inbox 멱등성 키를 찾을 수 있도록 한다.
    private void sendToDlt(Outbox outbox) {
        String dltTopic = outbox.getEventType() + ".DLT";
        try {
            // domainId 는 entity 상 nullable 가능성 있어 null 가드.
            String key = outbox.getDomainId() != null ? outbox.getDomainId().toString() : null;
            ProducerRecord<String, String> record = new ProducerRecord<>(dltTopic, key, outbox.getPayload());
            record.headers().add("message_id", outbox.getId().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("correlation_id", outbox.getCorrelationId().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record)
                    .whenComplete((res, e) -> {
                        if (e != null) log.error("DLT 전송 실패: correlationId={}", outbox.getCorrelationId(), e);
                        else log.info("DLT 전송 성공: correlationId={}, topic={}", outbox.getCorrelationId(), dltTopic);
                    });
        } catch (Exception e) {
            log.error("DLT 전송 중 예외 발생: correlationId={}", outbox.getCorrelationId(), e);
        }
    }
}
