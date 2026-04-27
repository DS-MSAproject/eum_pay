package com.eum.reviewserver.dto.response;

public record ReviewCreateDataDto(
        String publicId,
        String message,
        String redirectUrl
) {
}
