package com.eum.searchserver.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record ReviewSearchResponse(
        Long reviewId,
        Long productId,
        String writerName,
        Integer star,
        Long likeCount,
        List<String> reviewMediaUrls,
        String mediaType,
        String content,
        String createdAt,
        String reviewDetailUrl
) {
}
