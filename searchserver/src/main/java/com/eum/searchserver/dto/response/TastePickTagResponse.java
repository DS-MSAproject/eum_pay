package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record TastePickTagResponse(
        String brandName,
        String tagName,
        boolean selected
) {
}
