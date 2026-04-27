package com.eum.orderserver.message.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompletedEvent {

    private String eventId;
    private String eventType;
    private Long orderId;
    private Long userId;
    private Long amount;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private String producer;
    private Integer schemaVersion;

    public static OrderCompletedEvent of(Long orderId, Long userId, Long amount) {
        String eventId = UUID.randomUUID().toString();
        return OrderCompletedEvent.builder()
                .eventId(eventId)
                .eventType("OrderCompleted")
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .correlationId(String.valueOf(orderId))
                .causationId(eventId)
                .occurredAt(LocalDateTime.now())
                .producer("orderserver")
                .schemaVersion(1)
                .build();
    }
}