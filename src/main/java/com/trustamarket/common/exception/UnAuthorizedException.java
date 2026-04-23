package com.trustamarket.common.exception;

import org.springframework.http.HttpStatus;

// 401 Unauthorized — 인증 자체가 되지 않은 요청. 만료/위조 토큰 포함.
public class UnAuthorizedException extends CustomException {

    public UnAuthorizedException() {
        this("로그인이 필요한 서비스입니다.");
    }

    public UnAuthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public UnAuthorizedException(ErrorCodeSpec errorCode) {
        super(errorCode);
    }
}
