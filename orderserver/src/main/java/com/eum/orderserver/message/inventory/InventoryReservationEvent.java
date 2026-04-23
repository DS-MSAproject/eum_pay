package com.eum.orderserver.message.inventory;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationEvent {

    private String eventId;
    private String eventType;

    @JsonProperty("order_id")
    @JsonAlias({"orderId"})
    private Long orderId;

    private boolean success;
    private String reason;
    private List<Item> items;

    public String processedEventId(String fallbackType) {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        return fallbackType + ":" + orderId + ":" + success;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private Long optionId;
        private Long quantity;
    }
}
