package com.eum.reviewserver.dto.response;

public record ReviewDetailResponse(
        String status,
        ReviewDetailDto data
) {
}
