package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record TastePickProductResponse(
        Long productId,
        String imageUrl,
        String title,
        Long price,
        String brandName,
        String productUrl
) {
}
