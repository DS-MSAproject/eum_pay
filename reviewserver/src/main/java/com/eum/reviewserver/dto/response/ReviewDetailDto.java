package com.eum.reviewserver.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewDetailDto(
        Long reviewId,
        List<ReviewMediaDto> reviewMedias,
        String mediaType,
        long likeCount,
        String writerName,
        int star,
        int preferenceScore,
        int repurchaseScore,
        int freshnessScore,
        String content,
        LocalDateTime createAt,
        String reportUrl
) {
}
