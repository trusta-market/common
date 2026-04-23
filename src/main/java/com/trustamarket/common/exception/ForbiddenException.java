package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 403 Forbidden — 인증은 됐으나 권한이 부족한 상황.
public class ForbiddenException extends CustomException {

    private static final String DEFAULT_MESSAGE = "해당 작업에 대한 접근 권한이 없습니다.";

    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, DEFAULT_MESSAGE);
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCodeSpec errorCode) {
        super(errorCode);
    }
}
