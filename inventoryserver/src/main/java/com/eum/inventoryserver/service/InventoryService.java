package com.eum.inventoryserver.service;

import com.eum.inventoryserver.entity.Inventory;
import com.eum.inventoryserver.entity.InventoryReservation;
import com.eum.inventoryserver.entity.InventoryReservationItem;
import com.eum.inventoryserver.message.inventory.InventoryDeductionResult;
import com.eum.inventoryserver.message.inventory.InventoryStockResponse;
import com.eum.inventoryserver.message.order.OrderCheckedOutEvent;
import com.eum.inventoryserver.message.product.ProductReservationResult;
import com.eum.inventoryserver.message.product.ProductRestoreResult;
import com.eum.inventoryserver.repository.InventoryRepository;
import com.eum.inventoryserver.repository.InventoryReservationRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private static final long NO_OPTION_ID = 0L;

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    public Integer getStockQuantity(Long productId, Long optionId) {
        Long normalizedOptionId = normalizeOptionId(optionId);
        if (normalizedOptionId == null) {
            return inventoryRepository.findAllByProductId(productId).stream()
                    .mapToInt(Inventory::getStockQuantity)
                    .sum();
        }

        return inventoryRepository.findByProductIdAndOptionId(productId, normalizedOptionId)
                .map(Inventory::getStockQuantity)
                .orElse(0);
    }

    @Transactional
    public void decreaseStock(Long productId, Long optionId, int quantity) {
        Long normalizedOptionId = normalizeOptionId(optionId);
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, normalizedOptionId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보가 없습니다. Product: " + productId + ", Option: " + normalizedOptionId));

        inventory.removeStock(quantity);
    }

    @Transactional
    public void increaseStock(Long productId, Long optionId, int quantity) {
        Long normalizedOptionId = normalizeOptionId(optionId);
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, normalizedOptionId)
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder()
                                .productId(productId)
                                .optionId(normalizedOptionId)
                                .stockQuantity(0)
                                .build()
                ));

        inventory.addStock(quantity);
    }

    public Map<Long, Integer> getStockMap(List<Long> productIds) {
        return inventoryRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        Inventory::getStockQuantity,
                        Integer::sum
                ));
    }

    public List<InventoryStockResponse> getStocks(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        return inventoryRepository.findAllByProductIdIn(productIds).stream()
                .map(inventory -> InventoryStockResponse.builder()
                        .productId(inventory.getProductId())
                        .optionId(toExternalOptionId(inventory.getOptionId()))
                        .stockQuantity(inventory.getStockQuantity())
                        .stockStatus(resolveStockStatus(inventory.getStockQuantity()))
                        .build())
                .toList();
    }

    @Transactional
    public ProductReservationResult reserveOrderStock(OrderCheckedOutEvent event) {
        try {
            validateOrderCheckedOutEvent(event);
            return reservationRepository.findByOrderId(event.getOrderId())
                    .map(reservation -> toReservationResult(reservation, event.getCorrelationId()))
                    .orElseGet(() -> createOrderReservation(event));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ProductReservationResult.builder()
                    .orderId(event.getOrderId())
                    .correlationId(event.getCorrelationId())
                    .success(false)
                    .reason(e.getMessage())
                    .build();
        }
    }

    @Transactional
    public InventoryDeductionResult tryConfirmReservedStock(Long orderId, String correlationId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .success(false)
                    .reason("주문 재고 예약이 없습니다. orderId=" + orderId)
                    .build();
        }
        if (reservation.isConfirmed()) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .success(true)
                    .build();
        }
        if (reservation.isReleased() || reservation.isRejected()) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .success(false)
                    .reason("해제되었거나 거절된 예약은 확정할 수 없습니다. orderId=" + orderId)
                    .build();
        }

        for (InventoryReservationItem item : reservation.getItems()) {
            decreaseStock(item.getProductId(), item.getOptionId(), item.getQuantity());
        }
        reservation.confirm();
        return InventoryDeductionResult.builder()
                .orderId(orderId)
                .correlationId(correlationId)
                .success(true)
                .build();
    }

    @Transactional
    public ProductRestoreResult releaseReservedStock(Long orderId, String reason, String correlationId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElse(null);

        if (reservation == null) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .success(false)
                    .reason("주문 재고 예약이 없습니다. orderId=" + orderId)
                    .build();
        }

        if (reservation.isRejected()) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .success(false)
                    .reason("거절된 예약은 복구할 재고가 없습니다. orderId=" + orderId)
                    .build();
        }

        if (reservation.isReleased()) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .success(true)
                    .reason("이미 예약이 해제된 주문입니다.")
                    .build();
        }

        boolean stockWasDeducted = reservation.isConfirmed();
        if (stockWasDeducted) {
            for (InventoryReservationItem item : reservation.getItems()) {
                increaseStock(item.getProductId(), item.getOptionId(), item.getQuantity());
            }
        }
        reservation.release(reason);

        return ProductRestoreResult.builder()
                .orderId(orderId)
                .correlationId(correlationId)
                .success(true)
                .build();
    }

    @Transactional
    public void increaseOptionStock(Long productId, Long optionId, int quantity) {
        this.increaseStock(productId, optionId, quantity);
    }

    @Transactional
    public void replaceStock(Long productId, Long optionId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }

        Long normalizedOptionId = normalizeOptionId(optionId);
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, normalizedOptionId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보가 없습니다. Product: " + productId + ", Option: " + normalizedOptionId));

        int current = inventory.getStockQuantity();
        if (quantity > current) {
            inventory.addStock(quantity - current);
        } else if (quantity < current) {
            inventory.removeStock(current - quantity);
        }
    }

    private ProductReservationResult createOrderReservation(OrderCheckedOutEvent event) {
        List<InventoryReservationItem> reservationItems = event.getItems().stream()
                .map(item -> InventoryReservationItem.of(
                        item.getProductId(),
                        normalizeOptionId(item.getOptionId()),
                        toIntQuantity(item.getQuantity()),
                        item.getProductName(),
                        item.getOptionName(),
                        resolvePrice(item.getPrice())
                ))
                .toList();

        for (InventoryReservationItem item : reservationItems) {
            ensureStockAvailable(item.getProductId(), item.getOptionId(), item.getQuantity());
        }

        InventoryReservation reservation = reservationRepository.save(
                InventoryReservation.reserved(event.getOrderId(), event.processedEventId(), reservationItems)
        );
        return toReservationResult(reservation, event.getCorrelationId());
    }

    private void ensureStockAvailable(Long productId, Long optionId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, optionId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보가 없습니다. Product: " + productId + ", Option: " + optionId));

        if (inventory.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다. (현재 재고: " + inventory.getStockQuantity() + ")");
        }
    }

    private ProductReservationResult toReservationResult(InventoryReservation reservation, String correlationId) {
        boolean success = reservation.isReserved() || reservation.isConfirmed();
        return ProductReservationResult.builder()
                .orderId(reservation.getOrderId())
                .correlationId(correlationId)
                .success(success)
                .reason(success ? null : reservation.getReason())
                .items(success ? toReservedItems(reservation.getItems()) : List.of())
                .build();
    }

    private List<ProductReservationResult.ReservedItem> toReservedItems(List<InventoryReservationItem> items) {
        return items.stream()
                .map(item -> ProductReservationResult.ReservedItem.builder()
                        .productId(item.getProductId())
                        .optionId(toExternalOptionId(item.getOptionId()))
                        .productName(item.getProductName())
                        .optionName(item.getOptionName())
                        .price(resolvePrice(item.getPrice()))
                        .quantity(item.getQuantity().longValue())
                        .build())
                .toList();
    }

    private void validateOrderCheckedOutEvent(OrderCheckedOutEvent event) {
        if (event == null || event.getOrderId() == null) {
            throw new IllegalArgumentException("주문 이벤트에 orderId가 없습니다.");
        }
        if (event.getItems() == null || event.getItems().isEmpty()) {
            throw new IllegalArgumentException("주문 이벤트에 상품 목록이 없습니다. orderId=" + event.getOrderId());
        }
        event.getItems().forEach(item -> {
            if (item.getProductId() == null) {
                throw new IllegalArgumentException("주문 항목에 productId가 없습니다. orderId=" + event.getOrderId());
            }
            toIntQuantity(item.getQuantity());
        });
    }

    private int toIntQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다. quantity=" + quantity);
        }
        if (quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("주문 수량이 너무 큽니다. quantity=" + quantity);
        }
        return quantity.intValue();
    }

    private Long resolvePrice(Long price) {
        return price != null ? price : 0L;
    }

    private Long normalizeOptionId(Long optionId) {
        return optionId == null || optionId == NO_OPTION_ID ? null : optionId;
    }

    private Long toExternalOptionId(Long optionId) {
        return optionId == null ? NO_OPTION_ID : optionId;
    }

    private String resolveStockStatus(Integer stockQuantity) {
        return stockQuantity != null && stockQuantity > 0 ? "AVAILABLE" : "SOLDOUT";
    }
}
