package com.eum.reviewserver.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItemHistoryDto(
        Long id,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("order_state") String orderState
) {
}
