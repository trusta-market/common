package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 409 Conflict — 중복 생성, 낙관적 락 충돌 등 리소스 상태 불일치.
public class ConflictException extends CustomException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public ConflictException(ErrorCodeSpec errorCode) {
        super(errorCode);
    }
}
