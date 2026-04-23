package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 404 Not Found — 리소스 부재.
public class NotFoundException extends CustomException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    // 방어 — HttpStatus.NOT_FOUND 가 아닌 ErrorCode 를 넘기면 부모에서 IllegalArgumentException.
    // 클래스명이 약속하는 status 와 실제 응답 status 의 무음 불일치 차단.
    public NotFoundException(ErrorCodeSpec errorCode) {
        super(errorCode, HttpStatus.NOT_FOUND);
    }
}
