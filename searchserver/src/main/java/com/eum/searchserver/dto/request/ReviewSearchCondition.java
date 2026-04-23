package com.eum.searchserver.dto.request;

public record ReviewSearchCondition(
        Long productId,
        String keyword,
        String sortType,
        String reviewType,
        Integer page,
        Integer size
) {
}
