package com.eum.rag.common.response;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String code,
        String message,
        String path,
        OffsetDateTime timestamp
) {
}
