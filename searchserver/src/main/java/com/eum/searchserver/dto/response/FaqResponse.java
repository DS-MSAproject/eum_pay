package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record FaqResponse(
        Long faqId,
        String title,
        String author,
        String createdAt,
        Long viewCount,
        String faqDetailUrl
) {
}
