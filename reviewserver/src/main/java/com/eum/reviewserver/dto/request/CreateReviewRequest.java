package com.eum.reviewserver.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(
        @NotNull(message = "productId is null") Long productId,
        @NotNull(message = "star is null") @Min(value = 1, message = "star must be >= 1") @Max(value = 5, message = "star must be <= 5") Integer star,
        @NotNull(message = "preferenceScore is null") @Min(value = 1, message = "preferenceScore must be >= 1") @Max(value = 5, message = "preferenceScore must be <= 5") Integer preferenceScore,
        @NotNull(message = "repurchaseScore is null") @Min(value = 1, message = "repurchaseScore must be >= 1") @Max(value = 5, message = "repurchaseScore must be <= 5") Integer repurchaseScore,
        @NotNull(message = "freshnessScore is null") @Min(value = 1, message = "freshnessScore must be >= 1") @Max(value = 5, message = "freshnessScore must be <= 5") Integer freshnessScore,
        @NotBlank(message = "content is null") String content
) {
}
