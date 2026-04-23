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

    public InternalServerException(ErrorCodeSpec errorCode) {
        super(errorCode);
    }
}
