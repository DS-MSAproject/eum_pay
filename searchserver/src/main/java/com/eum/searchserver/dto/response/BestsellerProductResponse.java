package com.eum.searchserver.dto.response;

import lombok.Builder;

@Builder
public record BestsellerProductResponse(
        Long id,
        String imageUrl,
        String productTitle,
        Long price,

        // 💡 랭킹 배지는 UI에서 텍스트로 그려야 하므로 유지
        Integer salesRank,    // 1~6위
        String rankTag,       // "[판매 1위]" 등

        String productUrl
) {}