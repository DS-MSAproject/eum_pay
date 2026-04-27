package com.eum.searchserver.dto.request;

public record NoticeSearchCondition(
        String searchRange,  // 💡 일주일, 한달, 세달, 전체
        String searchType,   // 💡 제목, 내용 (글쓴이/아이디/별명 제외)
        String keyword,      // 검색어 입력창
        Integer page,            // 페이지 번호
        Integer size             // 페이지 당 노출 개수
) {}