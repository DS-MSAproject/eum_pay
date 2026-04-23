package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record ReviewSearchResponse(
        Long reviewId,
        Long productId,
        String writerName,
        Integer star,
        Long likeCount,
        String reviewMediaUrl,
        String mediaType,
        String content,
        String createdAt,
        String reviewDetailUrl
) {
}
