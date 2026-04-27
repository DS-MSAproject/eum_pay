package com.eum.boardserver.dto;

public record NoticeSearchCondition(
        String searchType, // "TITLE" (제목), "CONTENT" (내용), "ALL" (제목+내용)
        String keyword,    // 검색어 (이미지의 '오독')
        String category,   // [공지], [이벤트] 등
        Boolean isPinned
) {}
