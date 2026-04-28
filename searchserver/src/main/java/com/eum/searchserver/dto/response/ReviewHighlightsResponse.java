package com.eum.searchserver.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record ReviewHighlightsResponse(
        String status,
        String title,
        List<ReviewHighlightItemResponse> items
) {
}
