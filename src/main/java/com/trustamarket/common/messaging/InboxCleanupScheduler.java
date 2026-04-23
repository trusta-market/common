package com.trustamarket.common.messaging;

import com.trustamarket.common.domain.inbox.InboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Inbox 테이블 비대화 방지용 일괄 삭제 스케줄러. 매일 03:00 (UTC) 에 7일 이전 레코드 삭제.
// zone 을 UTC 로 고정해 서버 timezone 이 달라도 동일 시각에 실행되도록 한다.
@Slf4j
@RequiredArgsConstructor
public class InboxCleanupScheduler {

    private final InboxRepository inboxRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = inboxRepository.deleteByProcessedAtBefore(cutoff);
        log.info("Inbox 정리 완료: {}건 삭제 (기준: {})", deleted, cutoff);
    }
}
