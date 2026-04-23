package com.eum.orderserver.dto;

import com.eum.orderserver.domain.OrderState;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderDetailResponse {
    @JsonProperty("order_id")
    private Long orderId;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("user_name")
    private String userName;
    private Long amount;
    @JsonProperty("total_item_count")
    private Long totalItemCount;
    @JsonProperty("product_total_price")
    private Long productTotalPrice;
    @JsonProperty("payment_method")
    private String paymentMethod;
    @JsonProperty("paid_amount")
    private Long paidAmount;
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
    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
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
    }
}
