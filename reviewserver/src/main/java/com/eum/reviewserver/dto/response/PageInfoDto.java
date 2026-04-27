package com.eum.reviewserver.dto.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public record PageInfoDto(
        PageableDto pageable,
        int totalPages,
        long totalElements,
        boolean last,
        int size,
        int number,
        SortDto sort,
        int numberOfElements,
        boolean first,
        boolean empty
) {
    public static PageInfoDto from(Page<?> page) {
        Sort sort = page.getSort();
        return new PageInfoDto(
                new PageableDto(
                        sortDto(sort),
                        page.getPageable().getOffset(),
                        page.getPageable().getPageNumber(),
                        page.getPageable().getPageSize(),
                        page.getPageable().isPaged(),
                        page.getPageable().isUnpaged()
                ),
                page.getTotalPages(),
                page.getTotalElements(),
                page.isLast(),
                page.getSize(),
                page.getNumber(),
                sortDto(sort),
                page.getNumberOfElements(),
                page.isFirst(),
                page.isEmpty()
        );
    }

    private static SortDto sortDto(Sort sort) {
        return new SortDto(sort.isSorted(), sort.isUnsorted(), sort.isEmpty());
    }

    public record PageableDto(
            SortDto sort,
            long offset,
            int pageNumber,
            int pageSize,
            boolean paged,
            boolean unpaged
    ) {
    }

    public record SortDto(
            boolean sorted,
            boolean unsorted,
            boolean empty
    ) {
    }
}
