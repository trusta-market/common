package com.trustamarket.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.data.domain.Page;

// Trusta 표준 성공 응답 — 페이지네이션 버전. 구조: { status, data, pageInfo }.
// data 에는 Page 의 content 만 담아 Spring 내부 필드(pageable, sort)는 노출하지 않는다.
// CommonResponseAdvice 가 컨트롤러의 Page<?> 반환을 감지해 자동으로 of() 를 호출한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagedResponse<T>(
    int status,
    List<T> data,
    PageInfo pageInfo
) {

    public static <T> PagedResponse<T> of(int status, Page<T> page) {
        return new PagedResponse<>(status, page.getContent(), PageInfo.from(page));
    }
}
