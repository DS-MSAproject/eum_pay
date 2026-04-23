package com.eum.searchserver.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record FaqDetailResponse(
        Long faqId,
        String title,
        String author,
        String createdAt,
        Long viewCount,
        String content,
        List<String> contentImageUrls
) {
}
