package com.trustamarket.common.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Kafka 리스너 메서드에 붙여 InboxAdvice 의 멱등성 체크를 활성화하는 마커 애노테이션.
// value 는 컨슈머 그룹 식별자로, 같은 메시지도 그룹이 다르면 개별 처리된다.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsumer {
    String value();
}
