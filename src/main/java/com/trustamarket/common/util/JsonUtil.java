package com.trustamarket.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// ObjectMapper 를 감싸 Outbox payload 직/역직렬화에 사용되는 JSON 헬퍼.
// 체크 예외를 런타임으로 승격해 호출부가 try-catch 를 쓰지 않도록 한다.
@Slf4j
@RequiredArgsConstructor
public class JsonUtil {

    private final ObjectMapper objectMapper;

    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("JSON 직렬화 실패: {}", e.getMessage());
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }

    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON 역직렬화 실패: {}", e.getMessage());
            throw new RuntimeException("JSON 역직렬화 실패", e);
        }
    }

    // 제네릭 타입(List<Foo> 등) 역직렬화용. TypeReference 로 타입 소거 회피.
    public <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            log.error("JSON 역직렬화 실패: {}", e.getMessage());
            throw new RuntimeException("JSON 역직렬화 실패", e);
        }
    }
}
