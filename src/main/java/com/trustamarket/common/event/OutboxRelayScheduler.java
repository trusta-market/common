package com.trustamarket.common.event;

import com.trustamarket.common.domain.outbox.Outbox;
import com.trustamarket.common.domain.outbox.OutboxRepository;
import com.trustamarket.common.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.List;

// PENDING/FAILED 는 원 토픽으로, DLT_PENDING 은 DLT 토픽으로 각각 재시도하는 스케줄러.
// OutboxEventListener 의 AFTER_COMMIT 발행이 누락됐거나 DLT send 가 실패한 경우의 안전망.
// 엔트리별 상태 전이는 OutboxCallback / OutboxDltAckHandler(@Transactional REQUIRES_NEW) 가 담당.
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxCallback outboxCallback;
    private final OutboxDltAckHandler dltAckHandler;

    // 10초 주기. 메인 재시도(retryCount < 3) + DLT 재시도(DLT_PENDING).
    @Scheduled(fixedDelay = 10000)
    public void relay() {
        List<Outbox> mainRetryables = outboxRepository.findByStatusInAndRetryCountLessThan(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), MAX_RETRY_COUNT);
        for (Outbox outbox : mainRetryables) {
            retryMain(outbox);
        }

        List<Outbox> dltPending = outboxRepository.findByStatus(OutboxStatus.DLT_PENDING);
        for (Outbox outbox : dltPending) {
            retryDlt(outbox);
        }
    }

    // 메인 토픽 재발행 — 성공/실패 상태 전이는 OutboxCallback(REQUIRES_NEW) 에 위임.
    private void retryMain(Outbox outbox) {
        try {
            ProducerRecord<String, String> record = buildRecord(outbox.getEventType(), outbox);
            kafkaTemplate.send(record).get();
            outboxCallback.onSuccess(outbox.getCorrelationId());
        } catch (Exception e) {
            outboxCallback.onFailure(outbox.getCorrelationId(), e);
            log.error("Outbox Relay 실패: correlationId={}", outbox.getCorrelationId(), e);
        }
    }

    // DLT 토픽 재발행 — 성공 시 DLT_SENT 전이(OutboxDltAckHandler), 실패 시 DLT_PENDING 유지.
    private void retryDlt(Outbox outbox) {
        String dltTopic = outbox.getEventType() + ".DLT";
        try {
            ProducerRecord<String, String> record = buildRecord(dltTopic, outbox);
            kafkaTemplate.send(record).get();
            dltAckHandler.markDltSent(outbox.getCorrelationId());
            log.info("DLT Relay 성공: correlationId={}, topic={}", outbox.getCorrelationId(), dltTopic);
        } catch (Exception e) {
            log.error("DLT Relay 실패 (DLT_PENDING 유지, 다음 주기 재시도): correlationId={}",
                    outbox.getCorrelationId(), e);
        }
    }

    // 공통 ProducerRecord 빌더 — domainId nullable 가드 + message_id/correlation_id 헤더.
    private ProducerRecord<String, String> buildRecord(String topic, Outbox outbox) {
        String key = outbox.getDomainId() != null ? outbox.getDomainId().toString() : null;
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, outbox.getPayload());
        record.headers().add("message_id", outbox.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlation_id", outbox.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
