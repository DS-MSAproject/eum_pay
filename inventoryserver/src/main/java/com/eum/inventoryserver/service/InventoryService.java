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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 재고 원장 변경과 주문 재고 예약을 담당하는 inventoryserver의 핵심 도메인 서비스입니다.
 *
 * Inventory 엔티티의 수량 변경, InventoryReservation 상태 전환, 삭제 마커 확인을 한 곳에서 처리하고
 * 주문 재고 예약/차감 결과는 InventoryOutboxService를 통해 orderserver가 소비할 이벤트로 남깁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private static final long NO_OPTION_ID = 0L;

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    // 1. 현재 재고 조회 (상품 혹은 옵션)
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

    // 2. 재고 차감 (비관적 락 적용)
    @Transactional
    public void decreaseStock(Long productId, Long optionId, int quantity) {
        Long normalizedOptionId = normalizeOptionId(optionId);
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, normalizedOptionId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보가 없습니다. Product: " + productId + ", Option: " + normalizedOptionId));

        inventory.removeStock(quantity);
    }

    // 3. 재고 증가 (초기 등록 및 복구 시 사용)
    @Transactional
    public void increaseStock(Long productId, Long optionId, int quantity) {
        Long normalizedOptionId = normalizeOptionId(optionId);
        // 기존 재고 레코드가 없으면 새로 생성, 있으면 가져오기
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

    // 4. 목록 조회를 위한 Batch 조회 (Map<ProductId, TotalStock>)
    public Map<Long, Integer> getStockMap(List<Long> productIds) {
        // 여러 옵션의 재고를 합산해서 상품별 총 재고를 줄지,
        // 혹은 대표 재고만 줄지에 따라 쿼리가 달라집니다.
        // 여기서는 단순 상품 ID 매핑을 유지합니다.
        return inventoryRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        Inventory::getStockQuantity,
                        Integer::sum // 동일 상품 ID가 여러 개(옵션별) 있을 경우 합산
                ));
    }

    // productserver가 상품 목록/상세 출력 시 FeignClient로 호출하는 현재 재고 조회입니다.
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

    /**
     * 재고 예약 단계. 같은 orderId 예약이 이미 있으면 멱등하게 기존 결과를 반환한다.
     * 재고 부족 등 비즈니스 실패는 예외를 던지지 않고 실패 결과를 반환해 호출자 TX가 오염되지 않도록 한다.
     */
    @Transactional
    public ProductReservationResult reserveOrderStock(OrderCheckedOutEvent event) {
        try {
            validateOrderCheckedOutEvent(event);
            return reservationRepository.findByOrderId(event.getOrderId())
                    .map(this::toReservationResult)
                    .orElseGet(() -> createOrderReservation(event));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ProductReservationResult.builder()
                    .orderId(event.getOrderId())
                    .success(false)
                    .reason(e.getMessage())
                    .build();
        }
    }

    /**
     * 재고 차감 확정 단계. 결제 완료 이벤트 수신 시 예약 항목만큼 실제 재고를 차감한다.
     * 비즈니스 실패는 예외 대신 결과 객체로 반환해 호출자 TX가 오염되지 않도록 한다.
     */
    @Transactional
    public InventoryDeductionResult tryConfirmReservedStock(Long orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId).success(false).reason("주문 재고 예약이 없습니다. orderId=" + orderId).build();
        }
        if (reservation.isConfirmed()) {
            return InventoryDeductionResult.builder().orderId(orderId).success(true).build();
        }
        if (reservation.isReleased() || reservation.isRejected()) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId).success(false)
                    .reason("해제되었거나 거절된 예약은 확정할 수 없습니다. orderId=" + orderId).build();
        }
        for (InventoryReservationItem item : reservation.getItems()) {
            decreaseStock(item.getProductId(), item.getOptionId(), item.getQuantity());
        }
        reservation.confirm();
        return InventoryDeductionResult.builder().orderId(orderId).success(true).build();
    }

    // 결제 실패 또는 결제 취소 시 예약 재고를 복구한다.
    @Transactional
    public ProductRestoreResult releaseReservedStock(Long orderId, String reason) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElse(null);

        if (reservation == null) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .reason("주문 재고 예약이 없습니다. orderId=" + orderId)
                    .build();
        }

        if (reservation.isRejected()) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .reason("거절된 예약은 복구할 재고가 없습니다. orderId=" + orderId)
                    .build();
        }

        if (reservation.isReleased()) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
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
                .success(true)
                .build();
    }

    // 옵션 전용 재고 증가 (컨트롤러 가독성을 위해 추가)
    @Transactional
    public void increaseOptionStock(Long productId, Long optionId, int quantity) {
        // 사실 내부 로직은 동일하므로 기존 increaseStock을 호출합니다.
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

    // OrderCheckedOut 이벤트를 실제 InventoryReservation 엔티티로 변환합니다.
    // 이 단계에서는 수량만 확인하고, 실제 재고 차감은 결제 완료 이벤트에서 수행합니다.
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
        return toReservationResult(reservation);
    }

    private void ensureStockAvailable(Long productId, Long optionId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, optionId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보가 없습니다. Product: " + productId + ", Option: " + optionId));

        if (inventory.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다. (현재 재고: " + inventory.getStockQuantity() + ")");
        }
    }

    // 예약 엔티티를 orderserver가 받을 수 있는 예약 결과 payload로 변환합니다.
    // RESERVED/CONFIRMED는 성공, REJECTED/RELEASED는 실패 또는 복구 완료 이후 상태로 봅니다.
    private ProductReservationResult toReservationResult(InventoryReservation reservation) {
        boolean success = reservation.isReserved() || reservation.isConfirmed();
        return ProductReservationResult.builder()
                .orderId(reservation.getOrderId())
                .success(success)
                .reason(success ? null : reservation.getReason())
                .items(success ? toReservedItems(reservation.getItems()) : List.of())
                .build();
    }

    // 예약된 항목 정보를 주문 상세 스냅샷으로 전달하기 위한 DTO로 변환합니다.
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

    // Kafka 이벤트 payload의 필수값을 확인합니다.
    // 잘못된 이벤트는 예약 실패로 흘려보내지 않고 명확한 예외 메시지를 남깁니다.
    private void validateOrderCheckedOutEvent(OrderCheckedOutEvent event) {
        if (event == null || event.getOrderId() == null) {
            throw new IllegalArgumentException("주문 이벤트에 orderId가 없습니다.");
        }
        if (event.getItems() == null || event.getItems().isEmpty()) {
            throw new IllegalArgumentException("주문 이벤트에 상품 항목이 없습니다. orderId=" + event.getOrderId());
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
