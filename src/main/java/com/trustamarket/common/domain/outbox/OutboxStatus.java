package com.trustamarket.common.domain.outbox;

// Outbox 메시지의 발행 상태.
// PENDING: 초기 저장 상태. Kafka 로 아직 발행되지 않음.
// PROCESSED: Kafka 발행 성공.
// FAILED: 발행 실패 — OutboxRelayScheduler 가 주기적으로 재시도.
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}
