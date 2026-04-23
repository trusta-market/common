package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 404 Not Found — 리소스 부재.
public class NotFoundException extends CustomException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public NotFoundException(ErrorCodeSpec errorCode) {
        super(errorCode);
    }
}
