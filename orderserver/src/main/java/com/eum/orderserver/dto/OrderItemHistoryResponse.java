package com.eum.orderserver.dto;

import com.eum.orderserver.domain.OrderState;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Builder
@ToString
public class OrderItemHistoryResponse {

    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("option_id")
    private Long optionId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("option_name")
    private String optionName;

    private Long price;
    private Long quantity;

    @JsonProperty("total_price")
    private Long totalPrice;

    @JsonProperty("order_state")
    private OrderState orderState;

    @JsonProperty("failed_reason")
    private String failedReason;

    @JsonProperty("failed_at")
    private LocalDateTime failedAt;
}