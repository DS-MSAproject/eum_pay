package com.eum.searchserver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import java.util.List;
import java.util.Map;

@Builder
public record SearchPageResponse<T>(
        String status,
        long totalElements,      // 💡 이름을 범용적으로 변경 (Product -> Elements)
        int totalPages,
        int currentPage,
        int size,

        boolean isFirst,
        boolean isLast,
        boolean hasNext,
        boolean hasPrevious,

        // 💡 [핵심] 도메인별 추가 정보를 담는 유연한 통로
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, Object> extra,

        List<T> data
) {
    public static <T> SearchPageResponse<T> of(List<T> data, long totalElements, int page, int size) {
        return of(data, totalElements, page, size, null);
    }

    public static <T> SearchPageResponse<T> of(List<T> data, long totalElements, int page, int size, Map<String, Object> extra) {
        int totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / size);

        return SearchPageResponse.<T>builder()
                .status("success")
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .size(size)
                .isFirst(page == 0)
                .isLast(page >= totalPages - 1)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .extra(extra) // 💡 상황에 따라 TrendingKeywords나 MenuTitle을 주입
                .data(data)
                .build();
    }
}