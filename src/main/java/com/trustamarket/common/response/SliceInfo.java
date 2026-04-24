package com.trustamarket.common.response;

import lombok.Builder;
import org.springframework.data.domain.Slice;

// SlicedResponse 의 sliceInfo 필드
// Spring Slice 의 메타만 담음요

// Page 와 달리 totalElements/totalPages 없음
// COUNT 쿼리 없이 hasNext 로 다음 슬라이스 존재 여부만 앎
// 큰 테이블에서 COUNT 비용 회피 목적도 있음
@Builder
public record SliceInfo(
    int page,
    int size,
    boolean first,
    boolean last,
    boolean hasNext
) {

    public static SliceInfo from(Slice<?> slice) {
        return SliceInfo.builder()
            .page(slice.getNumber())
            .size(slice.getSize())
            .first(slice.isFirst())
            .last(slice.isLast())
            .hasNext(slice.hasNext())
            .build();
    }
}
