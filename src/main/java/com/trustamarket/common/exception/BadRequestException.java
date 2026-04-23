package com.trustamarket.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// 400 Bad Request — 입력값 부적합 등.
@Getter
public class BadRequestException extends CustomException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    // 특정 필드에 귀속된 검증 에러를 표현할 때 사용.
    public BadRequestException(String message, String field) {
        super(HttpStatus.BAD_REQUEST, message, field);
    }

    // 방어 — HttpStatus.BAD_REQUEST 가 아닌 ErrorCode 를 넘기면 부모에서 IllegalArgumentException.
    // 클래스명이 약속하는 status 와 실제 응답 status 의 무음 불일치 차단.
    public BadRequestException(ErrorCodeSpec errorCode) {
        super(errorCode, HttpStatus.BAD_REQUEST);
    }
}
