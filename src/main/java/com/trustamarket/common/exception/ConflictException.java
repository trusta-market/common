package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 409 Conflict — 중복 생성, 낙관적 락 충돌 등 리소스 상태 불일치.
public class ConflictException extends CustomException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    // 방어 — HttpStatus.CONFLICT 가 아닌 ErrorCode 를 넘기면 부모에서 IllegalArgumentException.
    // 클래스명이 약속하는 status 와 실제 응답 status 의 무음 불일치 차단.
    public ConflictException(ErrorCodeSpec errorCode) {
        super(errorCode, HttpStatus.CONFLICT);
    }
}
