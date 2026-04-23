package com.trustamarket.common.domain.inbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

// Inbox 패턴 테이블 — Kafka 메시지의 중복 소비 방지.
// InboxAdvice 가 message_id 헤더를 id 로 사용해 save 시도하며, unique 제약 위반이면 중복으로 판단.
@Entity
@Table(name = "P_INBOX", indexes = {
        @Index(name = "idx_inbox_message_group", columnList = "messageGroup"),
        @Index(name = "idx_inbox_processed_at", columnList = "processedAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Inbox {

    // Kafka 메시지 헤더의 message_id 값. 중복 체크 키.
    @Id
    private UUID id;

    // 컨슈머 그룹 식별자 (예: "product-order-consumer"). 같은 메시지도 다른 그룹이면 개별 처리.
    private String messageGroup;

    // 최초 처리 시각. InboxCleanupScheduler 가 7일 기준으로 삭제할 때 사용.
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime processedAt;

    public Inbox(UUID id, String messageGroup) {
        this.id = id;
        this.messageGroup = messageGroup;
    }
}
