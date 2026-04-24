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

    // 메인 토픽 재발행 — send 성공 후 상태 업데이트 실패는 재발행 금지 (이미 발행된 메시지 중복 방지).
    // send 실패만 onFailure 로 넘기고, post-send 상태 업데이트 실패는 로그만 남긴다.
    private void retryMain(Outbox outbox) {
        ProducerRecord<String, String> record = buildRecord(outbox.getEventType(), outbox);
        try {
            kafkaTemplate.send(record).get();
        } catch (Exception e) {
            outboxCallback.onFailure(outbox.getCorrelationId(), e);
            log.error("Outbox Relay send 실패: correlationId={}", outbox.getCorrelationId(), e);
            return;
        }
        // 여기 도달 = Kafka ack 성공. 상태 갱신 실패해도 재발행하지 않는다.
        try {
            outboxCallback.onSuccess(outbox.getCorrelationId());
        } catch (Exception e) {
            log.error("Outbox 상태 갱신 실패 (Kafka 발행은 성공, 수동 확인 필요): correlationId={}",
                    outbox.getCorrelationId(), e);
        }
    }

    // DLT 토픽 재발행 — send 성공 시 DLT_SENT 전이, 실패 시 DLT_PENDING 유지.
    // send 와 상태 전이 try 를 분리해 중복 발행 방지.
    private void retryDlt(Outbox outbox) {
        String dltTopic = outbox.getEventType() + ".DLT";
        ProducerRecord<String, String> record = buildRecord(dltTopic, outbox);
        try {
            kafkaTemplate.send(record).get();
        } catch (Exception e) {
            log.error("DLT Relay send 실패 (DLT_PENDING 유지, 다음 주기 재시도): correlationId={}",
                    outbox.getCorrelationId(), e);
            return;
        }
        // 여기 도달 = DLT ack 성공. 상태 갱신 실패해도 재발행하지 않는다.
        try {
            dltAckHandler.markDltSent(outbox.getCorrelationId());
            log.info("DLT Relay 성공: correlationId={}, topic={}", outbox.getCorrelationId(), dltTopic);
        } catch (Exception e) {
            log.error("DLT_SENT 전이 실패 (DLT 발행은 성공, 수동 확인 필요): correlationId={}",
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
