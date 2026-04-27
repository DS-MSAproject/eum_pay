package com.eum.reviewserver.dto.response;

public record ReviewCreateDataDto(
        Long reviewId,
        String message,
        String redirectUrl
) {
}
