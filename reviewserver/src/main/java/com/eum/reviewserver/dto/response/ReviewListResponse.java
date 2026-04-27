package com.eum.reviewserver.dto.response;

import java.util.List;

public record ReviewListResponse(
        String status,
        ReviewHeaderDto reviewHeader,
        List<ReviewBodyDto> reviewBody,
        PageInfoDto pageInfo
) {
}
