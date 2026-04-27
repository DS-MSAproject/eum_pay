package com.eum.orderserver.message.order;

import com.eum.common.correlation.CorrelationIdResolver;
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
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private String producer;
    private Integer schemaVersion;

    public static OrderCancelledEvent of(Long orderId, Long userId, String reason) {
        String eventId = UUID.randomUUID().toString();
        return OrderCancelledEvent.builder()
                .eventId(eventId)
                .eventType("OrderCancelled")
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .correlationId(CorrelationIdResolver.resolveOrGenerate(null))
                .causationId(eventId)
                .occurredAt(LocalDateTime.now())
                .producer("orderserver")
                .schemaVersion(1)
                .build();
    }
}
