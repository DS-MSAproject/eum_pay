package com.eum.reviewserver.dto.response;

public record ReviewUpdateResponse(
        String status,
        ReviewDetailDto data
) {
}
