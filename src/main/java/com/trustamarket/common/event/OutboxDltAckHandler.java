package com.trustamarket.common.event;

import com.trustamarket.common.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// DLT ack 성공 시 Outbox 를 DLT_SENT 로 전이하는 전용 빈.
// Kafka send 의 whenComplete 는 async 콜백이라 기존 트랜잭션 밖에서 실행된다.
// OutboxCallback 안에서 self-invocation 으로 @Transactional 호출하면 AOP 프록시가 적용되지 않아,
// 별도 빈으로 분리해 REQUIRES_NEW 트랜잭션이 확실히 시작되도록 한다.
@Slf4j
@RequiredArgsConstructor
public class OutboxDltAckHandler {

    private final OutboxRepository outboxRepository;

    // DLT 토픽 발행이 ack 된 후 호출되어 최종 상태 DLT_SENT 로 전이.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDltSent(String correlationId) {
        outboxRepository.findByCorrelationId(correlationId).ifPresent(outbox -> {
            outbox.markDltSent();
            outboxRepository.save(outbox);
            log.info("DLT ack 확정 → DLT_SENT: correlationId={}", correlationId);
        });
    }
}
