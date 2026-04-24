package com.trustamarket.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.util.Objects;
import java.util.UUID;

// 유저 감사 필드까지 포함하는 베이스 엔티티.
// createdBy/updatedBy 는 AuditorAware 구현이 제공하는 UUID 로 자동 채움.
@Getter
@MappedSuperclass
public abstract class BaseUserEntity extends BaseEntity {

    // 생성한 유저 UUID. 이후 변경 불가.
    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;

    // 마지막 수정 유저 UUID.
    @LastModifiedBy
    private UUID updatedBy;

    // 소프트 삭제한 유저 UUID.
    private UUID deletedBy;

    // 삭제한 주체를 함께 기록하는 소프트 삭제. BaseEntity.delete() 로 deletedAt 도 세팅.
    // 이미 삭제된 엔티티면 최초 삭제자/시각을 그대로 보존 (idempotent).
    // null userId 는 감사 이력 공백을 만들 수 있어 애초에 차단 — 호출부가 반드시 유효한 userId 를 넘겨야 한다.
    public void delete(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (isDeleted()) {
            return;
        }
        super.delete();
        this.deletedBy = userId;
    }

    // 부모 no-arg delete() 를 오버라이드해 호출 차단 — actor-aware 삭제 정책 강제.
    // BaseUserEntity 에선 반드시 delete(UUID) 를 사용해야 deletedBy 가 함께 기록된다.
    @Override
    public void delete() {
        throw new UnsupportedOperationException(
                "BaseUserEntity 는 delete(UUID userId) 를 사용하세요. 감사 이력 기록을 위해 삭제자 UUID 필수.");
    }
}
