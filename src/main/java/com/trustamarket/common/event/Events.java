package com.trustamarket.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

// 도메인 코드에서 ApplicationEventPublisher 주입 없이 OutboxEvent 를 발행할 수 있도록 하는 정적 게이트웨이.
// EventConfig 에서 빈으로 등록될 때 생성자가 publisher 를 static 필드에 저장한다.
@Slf4j
public class Events {

    private static ApplicationEventPublisher publisher;

    public Events(ApplicationEventPublisher publisher) {
        Events.publisher = publisher;
    }

    // 도메인 서비스에서 Events.trigger(OutboxEvent.of(...)) 형태로 호출.
    // publisher 미초기화 시 조용히 drop 하지 않고 warn 로그로 남김 — 빈 등록 누락/초기화 순서 문제를 드러내기 위함.
    public static void trigger(OutboxEvent event) {
        if (publisher == null) {
            log.warn("Events.publisher 미초기화 — 이벤트 drop: eventType={}, correlationId={}",
                    event.eventType(), event.correlationId());
            return;
        }
        publisher.publishEvent(event);
    }
}
