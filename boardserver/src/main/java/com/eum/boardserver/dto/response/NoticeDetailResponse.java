package com.eum.boardserver.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record NoticeDetailResponse(
        Long id,
        String category,
        String title,
        String content,
        Boolean isPinned,
        List<String> contentImageUrls,
        List<Map<String, Object>> actions,
        String createdAt,
        String updatedAt
) {
}
