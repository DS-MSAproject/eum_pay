package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record TogetherProductOptionResponse(
        Long optionId,
        String optionName,
        Long extraPrice,
        Integer initialStock
) {
}
