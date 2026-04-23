package com.trustamarket.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 모든 엔티티가 상속하는 베이스 감사 엔티티.
// createdAt/updatedAt 은 JPA Auditing 이 자동 채움. deletedAt 은 소프트 삭제 용도.
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    // 최초 저장 시점. 이후 변경 불가.
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시점. JPA 가 save 시 자동 갱신.
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 소프트 삭제 시각. null 이면 활성.
    private LocalDateTime deletedAt;

    // 소프트 삭제: 실제 row 는 남기고 deletedAt 만 세팅.
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    // 삭제 여부 플래그.
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
