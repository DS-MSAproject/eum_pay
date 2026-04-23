package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record TrendingKeywordResponse(
        int rank,
        String keyword,
        Double score // 💡 랭킹 산정 기준 확인을 위해 score를 포함하는 것이 완벽합니다.
) {
    /**
     * 정적 팩토리 메서드 추가
     */
    public static TrendingKeywordResponse of(int rank, String keyword, Double score) {
        return TrendingKeywordResponse.builder()
                .rank(rank)
                .keyword(keyword)
                .score(score)
                .build();
    }
}