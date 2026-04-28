package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record ReviewHighlightItemResponse(
        Long id,
        String img,
        String title,
        String rating,
        String href
) {
}
