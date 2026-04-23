package com.eum.boardserver.dto.request;

import java.util.List;
import java.util.Map;

public record FaqCreateRequest(
        String title,
        String content,
        String author,
        String category,
        Boolean isPinned,
        List<String> contentImageUrls,
        List<Map<String, Object>> actions
) {
}
