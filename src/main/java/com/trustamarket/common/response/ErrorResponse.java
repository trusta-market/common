package com.trustamarket.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import org.springframework.http.HttpStatus;

// RFC 9457 (Problem Details) 엄격 준수 에러 응답. 표준 5필드만: type/title/status/detail/instance.
// extension 필드(errors, timestamp 등) 금지. 복수 validation 에러는 detail 문자열에 요약한다.
// type 은 도메인 에러 문서 URI 권장, 모르면 ABOUT_BLANK("about:blank") 사용.
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance
) {

    // RFC 9457 §4.1 권장값 — "에러 타입 정보 없음" 기본값.
    public static final String ABOUT_BLANK = "about:blank";

    // type URI 없이 ABOUT_BLANK 로 응답 생성. 범용 400/500 에러 등에 사용.
    public static ErrorResponse of(HttpStatus status, String title, String detail, String instance) {
        return ErrorResponse.builder()
            .type(ABOUT_BLANK)
            .title(title)
            .status(status.value())
            .detail(detail)
            .instance(instance)
            .build();
    }

    // 도메인별 type URI 를 명시해 응답 생성.
    public static ErrorResponse of(String type, HttpStatus status, String title, String detail, String instance) {
        return ErrorResponse.builder()
            .type(type)
            .title(title)
            .status(status.value())
            .detail(detail)
            .instance(instance)
            .build();
    }
}
