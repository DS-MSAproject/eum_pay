package com.eum.inventoryserver.message.order;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * orderserver에서 주문 생성 후 OrderCheckedOut 토픽으로 전달되는 주문 이벤트 payload입니다.
 *
 * inventoryserver는 이 이벤트의 items를 기준으로 재고를 예약하고, 같은 이벤트가 재전달되면 eventId/orderId로 멱등 처리합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCheckedOutEvent {

    private String eventId;
    private String eventType;
    private Long orderId;
    private Long userId;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private String producer;
    private Integer schemaVersion;
    private Long amount;
    private Long totalPrice;
    private LocalDateTime capturedAt;

    @JsonProperty("receiver_name")
    private String receiverName;

    @JsonProperty("receiver_phone")
    private String receiverPhone;

    @JsonProperty("receiver_addr")
    private String receiverAddr;

    private List<OrderItem> items;

    public String processedEventId() {
        return eventId != null ? eventId : "ORDER_CHECKED_OUT:" + orderId;
    }

    public Long paymentAmount() {
        return amount != null ? amount : totalPrice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItem {
        @JsonProperty("productId")
        @JsonAlias({"product_id", "itemId", "item_id"})
        private Long productId;

        @JsonProperty("optionId")
        @JsonAlias({"option_id"})
        private Long optionId;

        @JsonProperty("quantity")
        @JsonAlias({"amount"})
        private Long quantity;

        @JsonProperty("productName")
        @JsonAlias({"product_name", "itemName", "item_name"})
        private String productName;

        @JsonProperty("optionName")
        @JsonAlias({"option_name"})
        private String optionName;

        private Long price;
        private Long discountPrice;
        private Long extraPrice;
        private Long lineTotalPrice;
        private LocalDateTime capturedAt;
    }
}
