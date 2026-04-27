package com.eum.reviewserver.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewBodyDto(
        String publicId,
        List<ReviewMediaDto> reviewMedias,
        long likeCount,
        String writerName,
        LocalDateTime createdAt,
        String content,
        int star,
        String mediaType,
        String reviewDetailUrl
) {
}
