package com.eum.searchserver.dto.request;

public record FaqSearchCondition(
        String searchRange,
        String searchType,
        String keyword,
        Integer page,
        Integer size
) {
}
