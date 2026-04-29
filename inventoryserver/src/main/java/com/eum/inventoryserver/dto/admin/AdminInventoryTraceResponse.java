package com.eum.inventoryserver.dto.admin;

import com.eum.inventoryserver.entity.Inventory;
import com.eum.inventoryserver.entity.InventoryReservation;
import com.eum.inventoryserver.entity.InventoryReservationItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminInventoryTraceResponse {

    private Long productId;
    private List<StockInfo> stockByOption;
    private List<ReservationInfo> reservations;

    @Getter
    @Builder
    public static class StockInfo {
        private Long optionId;
        private int stockQuantity;

        public static StockInfo from(Inventory inv) {
            return StockInfo.builder()
                    .optionId(inv.getOptionId())
                    .stockQuantity(inv.getStockQuantity())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ReservationInfo {
        private Long reservationId;
        private Long orderId;
        private String status;
        private int reservedQuantity;
        private LocalDateTime createdAt;

        public static ReservationInfo of(InventoryReservation r, Long productId) {
            int qty = r.getItems().stream()
                    .filter(i -> productId.equals(i.getProductId()))
                    .mapToInt(InventoryReservationItem::getQuantity)
                    .sum();
            return ReservationInfo.builder()
                    .reservationId(r.getId())
                    .orderId(r.getOrderId())
                    .status(r.getStatus().name())
                    .reservedQuantity(qty)
                    .createdAt(r.getCreatedAt())
                    .build();
        }
    }

    public static AdminInventoryTraceResponse of(Long productId,
                                                  List<Inventory> stocks,
                                                  List<InventoryReservation> reservations) {
        return AdminInventoryTraceResponse.builder()
                .productId(productId)
                .stockByOption(stocks.stream().map(StockInfo::from).toList())
                .reservations(reservations.stream().map(r -> ReservationInfo.of(r, productId)).toList())
                .build();
    }
}
