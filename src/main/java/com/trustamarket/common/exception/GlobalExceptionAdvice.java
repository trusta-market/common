package com.trustamarket.common.exception;

import com.trustamarket.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 전역 예외 처리기 — 모든 예외를 RFC 9457 엄격 준수 ErrorResponse 로 변환한다.
// 복수 필드 검증 오류는 extension 필드 없이 detail 에 "field: message; ..." 로 요약.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    // 도메인 코드가 던지는 CustomException — type 이 있으면 그대로, 없으면 ABOUT_BLANK.
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e, HttpServletRequest request) {
        log.warn("[{}] CustomException: {}", request.getRequestURI(), e.getMessage());

        String detail = buildDetail(e.getField(), e.getMessage());
        String type = e.getType() != null ? e.getType() : ErrorResponse.ABOUT_BLANK;

        ErrorResponse body = ErrorResponse.of(
            type,
            e.getStatus(),
            e.getStatus().getReasonPhrase(),
            detail,
            request.getRequestURI()
        );
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    // @Valid 실패 — 복수 에러를 detail 한 줄에 요약 (RFC 9457 extension 금지 원칙).
    // getAllErrors 로 필드 에러(FieldError) + class-level 에러(ObjectError) 모두 포함.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("[{}] Validation Error", request.getRequestURI());

        String detail = e.getBindingResult().getAllErrors().stream()
            .map(err -> err instanceof FieldError fe
                ? "%s: %s".formatted(fe.getField(), fe.getDefaultMessage())
                : "%s: %s".formatted(err.getObjectName(), err.getDefaultMessage()))
            .collect(Collectors.joining("; "));

        ErrorResponse body = ErrorResponse.of(
            HttpStatus.BAD_REQUEST,
            "Validation Error",
            detail,
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    // @Validated 가 파라미터에 걸렸을 때의 제약 위반. 복수 제약을 detail 한 줄에 요약.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        log.warn("[{}] Constraint Violation", request.getRequestURI());

        String detail = e.getConstraintViolations().stream()
            .map(cv -> "%s: %s".formatted(cv.getPropertyPath(), cv.getMessage()))
            .collect(Collectors.joining("; "));

        ErrorResponse body = ErrorResponse.of(
            HttpStatus.BAD_REQUEST,
            "Constraint Violation",
            detail,
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    // JSON 파싱 실패 — 원문 메시지는 내부용 정보가 많아 detail 엔 일반 문구만.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[{}] Message Not Readable: {}", request.getRequestURI(), e.getMessage());

        ErrorResponse body = ErrorResponse.of(
            HttpStatus.BAD_REQUEST,
            "Malformed Request Body",
            "요청 본문을 해석할 수 없습니다.",
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[{}] IllegalArgument: {}", request.getRequestURI(), e.getMessage());

        ErrorResponse body = ErrorResponse.of(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            e.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    // 낙관적 락 충돌 — 409 로 매핑해 재시도 유도.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        log.warn("[{}] Optimistic Lock: {}", request.getRequestURI(), e.getMessage());

        ErrorResponse body = ErrorResponse.of(
            HttpStatus.CONFLICT,
            "Conflict",
            "동시 수정 충돌이 발생했습니다.",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // 예상치 못한 예외 — 스택 트레이스는 로그에만, detail 엔 일반 문구만 노출해 민감 정보 유출 방지.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("[{}] Unhandled Exception: {}", request.getRequestURI(), e.getMessage(), e);

        ErrorResponse body = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "서버 내부 오류가 발생했습니다.",
            request.getRequestURI()
        );
        return ResponseEntity.internalServerError().body(body);
    }

    // field 정보가 있으면 "field: message", 없으면 message 그대로.
    private String buildDetail(String field, String message) {
        if (field == null || field.isBlank()) {
            return message;
        }
        return "%s: %s".formatted(field, message);
    }
}
