package com.trustamarket.common.event;

import org.springframework.context.ApplicationEventPublisher;

// 도메인 코드에서 ApplicationEventPublisher 주입 없이 OutboxEvent 를 발행할 수 있도록 하는 정적 게이트웨이.
// EventConfig 에서 빈으로 등록될 때 생성자가 publisher 를 static 필드에 저장한다.
public class Events {

    private static ApplicationEventPublisher publisher;

    public Events(ApplicationEventPublisher publisher) {
        Events.publisher = publisher;
    }

    // 도메인 서비스에서 Events.trigger(OutboxEvent.of(...)) 형태로 호출.
    public static void trigger(OutboxEvent event) {
        if (publisher != null) {
            publisher.publishEvent(event);
        }
    }
}
