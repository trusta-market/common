package com.trustamarket.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// 공통 라이브러리의 베이스 예외. 모든 도메인 예외는 이 클래스를 확장한다.
// ErrorCodeSpec 생성자로 도메인별 enum 기반 에러를, (status, message[, field]) 생성자로 단발성 에러를 표현.
@Getter
public class CustomException extends RuntimeException {

    // RFC 9457 type URI 로 매핑되는 에러 식별자. 없으면 GlobalExceptionAdvice 가 ABOUT_BLANK 로 대체.
    String type;
    private final HttpStatus status;
    // 입력 검증 에러의 필드명. GlobalExceptionAdvice 가 detail 에 "field: message" 로 합친다.
    private final String field;

    public CustomException(ErrorCodeSpec errorCode) {
        super(errorCode.getMessage());
        this.type = errorCode.getCode();
        this.status = errorCode.getStatus();
        this.field = errorCode.getField();
    }

    // 서브클래스가 자기 status 를 강제할 때 사용하는 방어적 생성자.
    // 예) ConflictException 이 HttpStatus.CONFLICT 를 넘겨 "Conflict 인데 응답은 404" 같은 무음 불일치를 throw 시점에 차단.
    // expected 와 errorCode.getStatus() 가 다르면 IllegalArgumentException 으로 fail-fast.
    public CustomException(ErrorCodeSpec errorCode, HttpStatus expected) {
        this(errorCode);
        if (errorCode.getStatus() != expected) {
            throw new IllegalArgumentException(
                    "%s 에는 status=%s 인 ErrorCode 만 허용됩니다 (실제: %s)"
                            .formatted(expected.getReasonPhrase(), expected, errorCode.getStatus()));
        }
    }

    public CustomException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.field = null;
    }

    public CustomException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }
}
