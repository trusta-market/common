package com.trustamarket.common.domain.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// Inbox 영속 리포지토리. PK 는 (messageId, messageGroup) 복합키(InboxId).
public interface InboxRepository extends JpaRepository<Inbox, InboxId> {

    // InboxCleanupScheduler 가 주기적으로 호출해 cutoff(=7일 전) 이전 레코드를 일괄 삭제한다.
    @Modifying
    @Query("DELETE FROM Inbox i WHERE i.processedAt < :cutoff")
    int deleteByProcessedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
