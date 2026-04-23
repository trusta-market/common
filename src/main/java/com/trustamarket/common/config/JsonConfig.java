package com.trustamarket.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trustamarket.common.util.JsonUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Jackson ObjectMapper 공통 설정.
// LocalDateTime 을 ISO 8601 문자열로 직렬화하고, 모르는 필드는 역직렬화 시 무시한다.
@Configuration
public class JsonConfig {

    // 전역 ObjectMapper. JavaTimeModule 등록 + 날짜는 문자열, 모르는 필드는 skip.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // ObjectMapper 래퍼. toJson / fromJson(Class/TypeReference) 제공.
    @Bean
    public JsonUtil jsonUtil(ObjectMapper objectMapper) {
        return new JsonUtil(objectMapper);
    }
}
