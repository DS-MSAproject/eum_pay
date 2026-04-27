package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record MainBannerProductResponse(
        Long productId,
        String imageUrl,
        Integer displayOrder,
        Boolean isHero
) {
}
