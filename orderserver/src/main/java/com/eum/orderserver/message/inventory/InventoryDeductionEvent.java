package com.eum.orderserver.message.inventory;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductionEvent {

    private String eventId;
    private String eventType;

    @JsonProperty("order_id")
    @JsonAlias({"orderId"})
    private Long orderId;

    @JsonAlias({"correlation_id", "correlationId"})
    private String correlationId;

    private boolean success;
    private String reason;

    public String processedEventId(String fallbackType) {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        return fallbackType + ":" + orderId + ":" + success;
    }
}
