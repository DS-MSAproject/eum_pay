package com.eum.reviewserver.dto.response;

public record ReviewHeaderDto(
        double starAverage,
        long totalReviewNumber,
        double ratioStar5,
        double ratioStar4,
        double ratioStar3,
        double ratioStar2,
        double ratioStar1,
        double preferenceRatio,
        double repurchaseRatio,
        double freshnessRatio
) {
}
