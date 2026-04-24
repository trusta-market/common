package com.trustamarket.common.domain;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDateTime;

// 표준 감사 엔티티. createdAt + updatedAt + 소프트 삭제까지.
// createdAt/updatedAt 은 부모(BaseTimeEntity)에서 상속. JPA Auditing 이 자동 채움.
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    // 소프트 삭제 시각. null 이면 활성.
    private LocalDateTime deletedAt;

    // 소프트 삭제: 실제 row 는 남기고 deletedAt 만 세팅.
    // 이미 삭제된 엔티티에 재호출돼도 최초 삭제 시각을 보존 (idempotent).
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    // 삭제 여부 플래그.
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
