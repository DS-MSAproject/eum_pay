package com.eum.searchserver.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record SimilarProductResponse(
        Long productId,
        String imageUrl,
        String title,
        List<String> tags,
        Long price
) {
}
