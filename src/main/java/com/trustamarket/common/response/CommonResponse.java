package com.trustamarket.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

// Trusta 표준 성공 응답 — 단일/일반 버전. 구조: { status, data }.
// 단일 조회, 명령(create/update/delete), 작은 고정 리스트에 사용.
// 페이지 응답은 PagedResponse 를 사용해 타입 시그니처만 보고도 구분할 수 있게 한다.
// data 가 null 인 응답(예: 본문 없는 성공)에서 필드 자동 생략 — PagedResponse 와 일관된 정책.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommonResponse<T>(
    int status,
    T data
) {

    public static <T> CommonResponse<T> of(int status, T data) {
        return new CommonResponse<>(status, data);
    }
}
