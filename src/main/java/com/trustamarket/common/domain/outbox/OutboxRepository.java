package com.trustamarket.common.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Outbox 영속 리포지토리.
public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    // OutboxRelayScheduler 가 재시도 대상을 조회할 때 사용.
    // (PENDING 또는 FAILED) AND retryCount < 3 인 메시지 반환.
    List<Outbox> findByStatusInAndRetryCountLessThan(List<OutboxStatus> statuses, int maxRetry);

    // SAGA 흐름에서 correlationId 로 기존 메시지를 찾을 때 사용.
    Optional<Outbox> findByCorrelationId(String correlationId);
}
