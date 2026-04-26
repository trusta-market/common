package com.trustamarket.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.data.domain.Slice;

// 표준 성공 응답 — Slice 버전
// 구조: { status, data, sliceInfo }
// 스크롤 / 더보기 용 — COUNT 쿼리 없이 hasNext 만으로 다음 페이지 존재 여부 판단
// Page<T> extends Slice<T> 이므로 CommonResponseAdvice 에서 Page 를 먼저 체크해야 함
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SlicedResponse<T>(
    int status,
    List<T> data,
    SliceInfo sliceInfo
) {

    public static <T> SlicedResponse<T> of(int status, Slice<T> slice) {
        return new SlicedResponse<>(status, slice.getContent(), SliceInfo.from(slice));
    }
}
