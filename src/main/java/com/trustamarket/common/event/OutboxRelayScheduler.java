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

// PENDING/FAILED 로 남은 Outbox 를 주기적으로 재발행하는 스케줄러.
// OutboxEventListener 의 AFTER_COMMIT 발행이 일시 장애로 누락됐을 때 안전망 역할.
// 엔트리별 상태 전이 + DLT 는 OutboxCallback(@Transactional REQUIRES_NEW) 이 담당 —
// 스케줄러 자체엔 트랜잭션 없고, 콜백 호출로 엔트리마다 독립 트랜잭션이 보장되어
// 루프 도중 한 건 실패가 다른 건 상태에 영향을 주지 않는다.
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxCallback outboxCallback;

    // 10초 주기로 재시도. retryCount < 3 인 PENDING/FAILED 만 대상.
    @Scheduled(fixedDelay = 10000)
    public void relay() {
        List<Outbox> pending = outboxRepository.findByStatusInAndRetryCountLessThan(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), MAX_RETRY_COUNT);

        for (Outbox outbox : pending) {
            try {
                // domainId nullable 가드 — 파티션 키로 쓰므로 null 이면 라운드로빈 파티션 배치.
                String key = outbox.getDomainId() != null ? outbox.getDomainId().toString() : null;
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        outbox.getEventType(), key, outbox.getPayload());
                record.headers().add("message_id", outbox.getId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("correlation_id", outbox.getCorrelationId().getBytes(StandardCharsets.UTF_8));

                kafkaTemplate.send(record).get();
                // 성공 전이는 REQUIRES_NEW 트랜잭션 안에서 처리.
                outboxCallback.onSuccess(outbox.getCorrelationId());
            } catch (Exception e) {
                // onFailure 가 retryCount++ / DLT 격리까지 책임. 스케줄러는 save 직접 호출하지 않는다.
                outboxCallback.onFailure(outbox.getCorrelationId(), e);
                log.error("Outbox Relay 실패: correlationId={}", outbox.getCorrelationId(), e);
            }
        }
    }
}
