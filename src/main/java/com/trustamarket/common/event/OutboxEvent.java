package com.trustamarket.common.event;

import java.util.UUID;

// Outbox 로 저장될 도메인 이벤트의 불변 데이터.
// of() 는 신규 correlationId 생성, withCorrelation() 은 SAGA 전파용.
public record OutboxEvent(
        String correlationId,
        String domainType,
        UUID domainId,
        String eventType,
        Object payload
) {
    // 최초 이벤트 발행 시 사용. correlationId 를 랜덤 UUID 로 생성.
    public static OutboxEvent of(String domainType, UUID domainId, String eventType, Object payload) {
        return new OutboxEvent(UUID.randomUUID().toString(), domainType, domainId, eventType, payload);
    }

    // SAGA 중간 단계에서 기존 correlationId 를 이어받아 새 이벤트를 발행할 때 사용.
    public static OutboxEvent withCorrelation(String correlationId, String domainType, UUID domainId,
                                              String eventType, Object payload) {
        return new OutboxEvent(correlationId, domainType, domainId, eventType, payload);
    }
}
