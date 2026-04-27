package com.eum.searchserver.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record TogetherProductResponse(
        Long productId,
        String imageUrl,
        String title,
        List<String> tags,
        Long price,
        List<TogetherProductOptionResponse> options
) {
}
