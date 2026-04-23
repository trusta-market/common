package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 500 Internal Server Error — 서버 내부 처리 실패. 상세 메시지는 로그에만 남기고 응답엔 일반화된 문구 권장.
public class InternalServerException extends CustomException {

    private static final String DEFAULT_MESSAGE = "서버 내부 오류가 발생했습니다.";

    public InternalServerException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, DEFAULT_MESSAGE);
    }

    public InternalServerException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    // 방어 — HttpStatus.INTERNAL_SERVER_ERROR 가 아닌 ErrorCode 를 넘기면 부모에서 IllegalArgumentException.
    // 클래스명이 약속하는 status 와 실제 응답 status 의 무음 불일치 차단.
    public InternalServerException(ErrorCodeSpec errorCode) {
        super(errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
