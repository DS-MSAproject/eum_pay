package com.eum.searchserver.dto.request;

import lombok.Builder;

public record ProductSearchCondition(
        String title,
        String keyword,
        String category,
        String subCategory,
        Long minPrice,
        Long maxPrice,
        String searchScope,
        String sortType,
        Integer page, // 💡 int -> Integer로 변경 (null 허용)
        Integer size  // 💡 int -> Integer로 변경 (null 허용)
) {
    // 💡 바인딩 시 null이 들어와도 NPE가 안 나도록 서비스 레이어에서 처리하거나
    // 아래처럼 명시적으로 기본값을 반환하는 getter 메서드(레코드 방식)를 추가합니다.
    public Integer page() { return page == null ? 0 : page; }
    public Integer size() { return size == null ? 12 : size; }
    public String category() { return category == null ? "ALL" : category; }
    public String sortType() { return sortType == null ? "최신순" : sortType; }
}
