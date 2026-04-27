package com.eum.boardserver.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record FaqDetailResponse(
        Long id,
        String category,
        String title,
        String content,
        String author,
        Long viewCount,
        Boolean isPinned,
        List<String> contentImageUrls,
        List<Map<String, Object>> actions,
        String createdAt,
        String updatedAt
) {
}
