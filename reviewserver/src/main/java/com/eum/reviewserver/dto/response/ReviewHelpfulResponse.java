package com.eum.reviewserver.dto.response;

public record ReviewHelpfulResponse(
        String status,
        String message,
        String reviewId,
        long likeCount
) {
}
