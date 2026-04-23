package com.trustamarket.common.event;

import com.trustamarket.common.domain.outbox.Outbox;
import com.trustamarket.common.domain.outbox.OutboxRepository;
import com.trustamarket.common.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

// PENDING/FAILED 로 남은 Outbox 를 주기적으로 재발행하는 스케줄러.
// OutboxEventListener 의 AFTER_COMMIT 발행이 일시 장애로 누락됐을 때 안전망 역할.
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 10초 주기로 재시도. retryCount < 3 인 PENDING/FAILED 만 대상.
    @Scheduled(fixedDelay = 10000)
    public void relay() {
        List<Outbox> pending = outboxRepository.findByStatusInAndRetryCountLessThan(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), MAX_RETRY_COUNT);

        for (Outbox outbox : pending) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        outbox.getEventType(), outbox.getPayload());
                record.headers().add("message_id", outbox.getId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("correlation_id", outbox.getCorrelationId().getBytes(StandardCharsets.UTF_8));

                kafkaTemplate.send(record).get();
                outbox.complete();
                outboxRepository.save(outbox);
                log.info("Outbox Relay 성공: correlationId={}, topic={}", outbox.getCorrelationId(), outbox.getEventType());
            } catch (Exception e) {
                outbox.fail();
                outboxRepository.save(outbox);
                log.error("Outbox Relay 실패: correlationId={}, retryCount={}", outbox.getCorrelationId(), outbox.getRetryCount());

                if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                    sendToDlt(outbox);
                }
            }
        }
    }

    // 상한 초과 시 "{원토픽}.DLT" 로 이관해 메인 큐에서 빼낸다.
    private void sendToDlt(Outbox outbox) {
        String dltTopic = outbox.getEventType() + ".DLT";
        try {
            kafkaTemplate.send(dltTopic, outbox.getPayload());
            log.warn("DLT 전송: correlationId={}, topic={}", outbox.getCorrelationId(), dltTopic);
        } catch (Exception e) {
            log.error("DLT 전송 실패: correlationId={}", outbox.getCorrelationId());
        }
    }
}
