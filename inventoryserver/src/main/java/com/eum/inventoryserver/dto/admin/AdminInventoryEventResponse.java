package com.eum.inventoryserver.dto.admin;

import com.eum.inventoryserver.entity.InventoryReservation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminInventoryEventResponse {

    private Long id;
    private Long orderId;
    private String status;
    private String sourceEventId;
    private String reason;
    private int itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminInventoryEventResponse from(InventoryReservation r) {
        return AdminInventoryEventResponse.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .status(r.getStatus().name())
                .sourceEventId(r.getSourceEventId())
                .reason(r.getReason())
                .itemCount(r.getItems().size())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
