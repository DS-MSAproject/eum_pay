package com.eum.searchserver.dto.response;


public record NoticeSearchResponse(
        Long id,
        String title,
        String content,
        String category,
        Boolean isPinned,
        String createdAt
) {}
