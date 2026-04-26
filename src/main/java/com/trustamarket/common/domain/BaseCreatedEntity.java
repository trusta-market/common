package com.trustamarket.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

// append-only 엔티티 (이벤트 로그, 이력, 트랜잭션 로그 등) 용 베이스.
// createdAt 만 있고 updatedAt / deletedAt 없음 — 생성 후 수정/삭제 불가한 테이블에 사용.
// 예) user_status_history, Order_Event_Log, p_point_transactions, delivery_tracking 등
// Instant 는 UTC 시점이라 PostgreSQL TIMESTAMPTZ 와 1:1 매핑.
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

    // 최초 저장 시점 (UTC). 이후 변경 불가.
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;
}
