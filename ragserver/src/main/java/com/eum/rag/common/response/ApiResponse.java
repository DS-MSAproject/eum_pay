package com.eum.rag.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "Request processed successfully.", data, OffsetDateTime.now());
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("SUCCESS", "Request processed successfully.", null, OffsetDateTime.now());
    }
}
