package com.trustamarket.common.response;

import lombok.Builder;
import org.springframework.data.domain.Page;

// PagedResponse 의 pageInfo 필드. Spring Page 의 메타만 담는다.
// page 는 0-based, size/totalElements/totalPages/first/last 는 Page 에서 그대로 전달.
@Builder
public record PageInfo(
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    public static PageInfo from(Page<?> page) {
        return PageInfo.builder()
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .build();
    }
}
