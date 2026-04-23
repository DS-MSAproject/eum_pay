package com.eum.reviewserver.dto.response;

public record ReviewCreateResponse(
        String status,
        ReviewCreateDataDto data
) {
}
