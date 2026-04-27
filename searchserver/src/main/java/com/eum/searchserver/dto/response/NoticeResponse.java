package com.eum.searchserver.dto.response;

import lombok.Builder;

// Notice 홈페이지 기본.
@Builder
public record NoticeResponse(
        Long id,
        String category,
        String title,
        Boolean isPinned,
        String noticeDetailUrl,
        String createdAt
) {}
