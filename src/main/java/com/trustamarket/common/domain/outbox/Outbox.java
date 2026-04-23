package com.trustamarket.common.domain.outbox;

import com.trustamarket.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Outbox 패턴 테이블. 도메인 트랜잭션 안에서 PENDING 으로 저장되고,
// 커밋 이후 OutboxEventListener 가 Kafka 로 발행하여 PROCESSED/FAILED 로 전이한다.
@Entity
@Table(name = "P_OUTBOX", indexes = @Index(name = "idx_outbox_status", columnList = "status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseEntity {

    // 메시지 고유 ID. Kafka 메시지 키로도 활용 가능.
    @Id
    private UUID id;

    // SAGA 상관 ID. 여러 서비스를 거치는 흐름을 묶기 위해 unique.
    @Column(unique = true)
    private String correlationId;

    // 이벤트가 속한 도메인 종류 (예: "ORDER", "PRODUCT").
    private String domainType;

    // 해당 도메인 엔티티 ID. Kafka 파티션 키로 사용하여 같은 리소스의 순서를 보장.
    private UUID domainId;

    // Kafka 토픽명.
    private String eventType;

    // JSON 직렬화된 이벤트 본문.
    @Column(columnDefinition = "TEXT")
    private String payload;

    // PENDING / PROCESSED / FAILED.
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    // 실패 재시도 카운트. 3회 초과 시 DLT 격리.
    private int retryCount;

    // 신규 생성 시 id 는 새 UUID, status=PENDING, retryCount=0 으로 초기화.
    @Builder
    public Outbox(String correlationId, String domainType, UUID domainId,
                  String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.correlationId = correlationId;
        this.domainType = domainType;
        this.domainId = domainId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    // Kafka 발행 성공 시 호출.
    public void complete() {
        this.status = OutboxStatus.PROCESSED;
    }

    // Kafka 발행 실패 시 호출. retryCount 증가.
    public void fail() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }
}
