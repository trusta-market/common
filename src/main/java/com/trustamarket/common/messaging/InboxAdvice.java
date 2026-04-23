package com.trustamarket.common.messaging;

import com.trustamarket.common.domain.inbox.Inbox;
import com.trustamarket.common.domain.inbox.InboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// @IdempotentConsumer 메서드에 대해 Inbox 기반 멱등 처리를 수행하는 AOP.
// message_id 를 PK 로 Inbox insert 를 시도하고, unique 위반이면 중복 메시지로 판단해 리스너를 건너뛴다.
@Slf4j
@Aspect
@RequiredArgsConstructor
public class InboxAdvice {

    private final InboxRepository inboxRepository;

    // 리스너와 같은 트랜잭션에서 Inbox 저장 + proceed. 실패 시 둘 다 롤백되어 재처리 가능.
    @Around("@annotation(idempotentConsumer)")
    @Transactional(rollbackFor = Exception.class)
    public Object handle(ProceedingJoinPoint joinPoint, IdempotentConsumer idempotentConsumer) throws Throwable {
        UUID messageId = extractMessageId(joinPoint.getArgs());

        if (messageId == null) {
            log.warn("message_id 헤더 없음, 멱등성 체크 건너뜀");
            return joinPoint.proceed();
        }

        try {
            Inbox inbox = new Inbox(messageId, idempotentConsumer.value());
            inboxRepository.saveAndFlush(inbox);
            log.debug("Inbox 기록 성공: {}", messageId);
        } catch (DataIntegrityViolationException e) {
            log.info("중복 메시지 무시: messageId={}", messageId);
            return null;
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            log.error("메시지 처리 실패, Inbox 롤백: messageId={}", messageId);
            throw throwable;
        }
    }

    // 리스너 파라미터에서 Kafka ConsumerRecord / Spring Message 헤더의 message_id 를 추출한다.
    private UUID extractMessageId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> record) {
                Header header = record.headers().lastHeader("message_id");
                if (header != null) return parseUuid(header.value());
            }
            if (arg instanceof Message<?> message) {
                Object header = message.getHeaders().get("message_id");
                if (header instanceof byte[] bytes) return parseUuid(bytes);
                if (header instanceof String str) return UUID.fromString(str);
            }
        }
        return null;
    }

    private UUID parseUuid(byte[] value) {
        try {
            return UUID.fromString(new String(value, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("message_id UUID 파싱 실패", e);
            return null;
        }
    }
}
