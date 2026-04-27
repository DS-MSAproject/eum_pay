package com.eum.boardserver.dto.request;

import java.util.List;
import java.util.Map;

public record NoticeCreateRequest(
        String title,
        String content,
        String category,
        Boolean isPinned,
        List<String> contentImageUrls,
        List<Map<String, Object>> actions // 💡 JSONB 매핑용
) {}
