package com.eum.searchserver.dto.response;

import java.util.Map;
import lombok.Builder;

@Builder
public record ReviewHeaderResponse(
        double avgRating,
        long totalCount,
        Map<Integer, Double> ratingDistribution
) {
}
