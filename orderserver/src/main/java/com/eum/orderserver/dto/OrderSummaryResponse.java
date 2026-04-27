package com.eum.orderserver.dto;

import com.eum.orderserver.domain.OrderState;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class OrderSummaryResponse {
    @JsonProperty("order_id")
    private Long orderId;
    @JsonProperty("user_id")
    private Long userId;
    private Long amount;
    @JsonProperty("receiver_name")
    private String receiverName;
    @JsonProperty("receiver_phone")
    private String receiverPhone;
    @JsonProperty("receiver_addr")
    private String receiverAddr;
    @JsonProperty("delete_yn")
    private String deleteYn;
    private LocalDateTime time;
    @JsonProperty("order_state")
    private OrderState orderState;
    @JsonProperty("failed_reason")
    private String failedReason;
    @JsonProperty("failed_at")
    private LocalDateTime failedAt;
}