package com.eum.inventoryserver.dto.admin;

import com.eum.inventoryserver.entity.InventoryReservation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
public class AdminInventoryLagAlertResponse {

    private Long reservationId;
    private Long orderId;
    private String sourceEventId;
    private long minutesStuck;
    private int itemCount;
    private LocalDateTime createdAt;

    public static AdminInventoryLagAlertResponse from(InventoryReservation r) {
        return AdminInventoryLagAlertResponse.builder()
                .reservationId(r.getId())
                .orderId(r.getOrderId())
                .sourceEventId(r.getSourceEventId())
                .minutesStuck(ChronoUnit.MINUTES.between(r.getCreatedAt(), LocalDateTime.now()))
                .itemCount(r.getItems().size())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
