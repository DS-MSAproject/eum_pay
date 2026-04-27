package com.eum.reviewserver.dto.response;

import java.util.List;

public record ReviewListResponse(
        String status,
        List<ReviewBodyDto> reviewBody,
        PageInfoDto pageInfo
) {
}
