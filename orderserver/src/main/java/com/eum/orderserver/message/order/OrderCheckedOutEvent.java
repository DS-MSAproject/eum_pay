package com.eum.orderserver.message.order;

import com.eum.common.correlation.CorrelationIdResolver;
import com.eum.orderserver.dto.product.CheckoutValidationResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    private List<Item> items;

    public static OrderCheckedOutEvent of(Long orderId, Long userId, String receiverName,
                                          String receiverPhone, String receiverAddr,
                                          Long amount, Long totalPrice, LocalDateTime capturedAt,
                                          List<CheckoutValidationResponse.Item> items,
                                          String correlationId) {
        String eventId = UUID.randomUUID().toString();
        return OrderCheckedOutEvent.builder()
                .eventId(eventId)
                .eventType("OrderCheckedOut")
                .orderId(orderId)
                .userId(userId)
                .correlationId(CorrelationIdResolver.resolveOrGenerate(correlationId))
                .causationId(eventId)
                .occurredAt(LocalDateTime.now())
                .producer("orderserver")
                .schemaVersion(1)
                .amount(amount)
                .totalPrice(totalPrice)
                .capturedAt(capturedAt)
                .receiverName(receiverName)
                .receiverPhone(receiverPhone)
                .receiverAddr(receiverAddr)
                .items(items.stream()
                        .map(Item::from)
                        .toList())
                .build();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("productId")
        private Long productId;

        @JsonProperty("optionId")
        private Long optionId;

        @JsonProperty("quantity")
        private Long quantity;

        @JsonProperty("productName")
        private String productName;

        @JsonProperty("optionName")
        private String optionName;

        private Long price;
        private Long discountPrice;
        private Long extraPrice;
        private Long lineTotalPrice;
        private LocalDateTime capturedAt;

        public static Item from(CheckoutValidationResponse.Item item) {
            return Item.builder()
                    .productId(item.getProductId())
                    .optionId(item.getOptionId())
                    .quantity(item.getQuantity())
                    .productName(item.getProductName())
                    .optionName(item.getOptionName())
                    .price(item.getPrice())
                    .discountPrice(item.getDiscountPrice())
                    .extraPrice(item.getExtraPrice())
                    .lineTotalPrice(item.getLineTotalPrice())
                    .capturedAt(item.getCapturedAt())
                    .build();
        }
    }
}
