package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record HomeBestsellerProductResponse(
        Integer rank,
        Long id,
        String imageUrl,
        String productTitle,
        Long price,
        Double score,
        Long salesCount,
        String createdAt,
        String productUrl
) {
}
