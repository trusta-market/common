package com.trustamarket.common.domain.outbox;

// Outbox 메시지의 발행 상태.
// PENDING: 초기 저장 상태. Kafka 로 아직 발행되지 않음.
// PROCESSED: Kafka 발행 성공. 최종 상태.
// FAILED: 발행 실패 — OutboxRelayScheduler 가 주기적으로 재시도 (retryCount < 3).
// DLT_PENDING: retryCount 상한 초과 후 DLT 로 격리 결정. DLT 전송 대기 또는 실패 후 재시도 필요.
// DLT_SENT: DLT 토픽에 ack 확인됨. 최종 상태 (수동 복구 전까지).
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED,
    DLT_PENDING,
    DLT_SENT
}
