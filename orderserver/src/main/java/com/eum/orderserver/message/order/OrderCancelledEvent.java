package com.eum.orderserver.message.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {

    private String eventId;
    private String eventType;
    private Long orderId;
    private Long userId;
    private String reason;
    private LocalDateTime occurredAt;

    public static OrderCancelledEvent of(Long orderId, Long userId, String reason) {
        return OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OrderCancelled")
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}