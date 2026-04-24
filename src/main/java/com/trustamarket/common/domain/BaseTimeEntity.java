package com.trustamarket.common.domain;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

// 생성 + 수정 타임스탬프 베이스. soft delete 없음.
// wallet/config/지갑 상태 등 라이프사이클이 status 로 관리되거나, 삭제 개념이 없는 엔티티에 사용.
// 예) p_wallets, mock_config, trust_score, Delivery, p_inspection_centers 등
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

    // 마지막 수정 시점. JPA 가 save 시 자동 갱신.
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
