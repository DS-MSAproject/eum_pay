package com.eum.inventoryserver.service;

import com.eum.common.correlation.CorrelationIdResolver;
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
                .orElseThrow(() -> new IllegalArgumentException("?ш퀬 ?뺣낫媛 ?놁뒿?덈떎. Product: " + productId + ", Option: " + normalizedOptionId));

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
                        Integer::sum // ?숈씪 ?곹뭹 ID媛 ?щ윭 媛??듭뀡蹂? ?덉쓣 寃쎌슦 ?⑹궛
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
                    .map(this::toReservationResult)
                    .orElseGet(() -> createOrderReservation(event));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ProductReservationResult.builder()
                    .orderId(event.getOrderId())
                    .correlationId(CorrelationIdResolver.resolveOrGenerate(event.getCorrelationId()))
                    .success(false)
                    .reason(e.getMessage())
                    .build();
        }
    }

    
    @Transactional
    public InventoryDeductionResult tryConfirmReservedStock(Long orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(CorrelationIdResolver.resolveOrGenerate(null))
                    .success(false)
                    .reason("주문 재고 예약이 없습니다. orderId=" + orderId)
                    .build();
        }
        if (reservation.isConfirmed()) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(CorrelationIdResolver.resolveOrGenerate(null))
                    .success(true)
                    .build();
        }
        if (reservation.isReleased() || reservation.isRejected()) {
            return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(CorrelationIdResolver.resolveOrGenerate(null))
                    .success(false)
                    .reason("해제했거나 거절된 예약은 확정할 수 없습니다. orderId=" + orderId)
                    .build();
        }
        for (InventoryReservationItem item : reservation.getItems()) {
            decreaseStock(item.getProductId(), item.getOptionId(), item.getQuantity());
        }
        reservation.confirm();
        return InventoryDeductionResult.builder()
                    .orderId(orderId)
                    .correlationId(CorrelationIdResolver.resolveOrGenerate(null))
                    .success(true)
                    .build();
    }

    @Transactional
    public ProductRestoreResult releaseReservedStock(Long orderId, String reason) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElse(null);

        if (reservation == null) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .reason("二쇰Ц ?ш퀬 ?덉빟???놁뒿?덈떎. orderId=" + orderId)
                    .build();
        }

        if (reservation.isRejected()) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .reason("嫄곗젅???덉빟? 蹂듦뎄???ш퀬媛 ?놁뒿?덈떎. orderId=" + orderId)
                    .build();
        }

        if (reservation.isReleased()) {
            return ProductRestoreResult.builder()
                    .orderId(orderId)
                    .success(true)
                    .reason("?대? ?덉빟???댁젣??二쇰Ц?낅땲??")
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

    @Transactional
    public void increaseOptionStock(Long productId, Long optionId, int quantity) {

        this.increaseStock(productId, optionId, quantity);
    }

    @Transactional
    public void replaceStock(Long productId, Long optionId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("?ш퀬??0 ?댁긽?댁뼱???⑸땲??");
        }

        Long normalizedOptionId = normalizeOptionId(optionId);
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, normalizedOptionId)
                .orElseThrow(() -> new IllegalArgumentException("?ш퀬 ?뺣낫媛 ?놁뒿?덈떎. Product: " + productId + ", Option: " + normalizedOptionId));

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
        return toReservationResult(reservation);
    }

    private void ensureStockAvailable(Long productId, Long optionId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdAndOptionIdWithLock(productId, optionId)
                .orElseThrow(() -> new IllegalArgumentException("?ш퀬 ?뺣낫媛 ?놁뒿?덈떎. Product: " + productId + ", Option: " + optionId));

        if (inventory.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("?ш퀬媛 遺議깊빀?덈떎. (?꾩옱 ?ш퀬: " + inventory.getStockQuantity() + ")");
        }
    }


    private ProductReservationResult toReservationResult(InventoryReservation reservation) {
        boolean success = reservation.isReserved() || reservation.isConfirmed();
        return ProductReservationResult.builder()
                .orderId(reservation.getOrderId())
                .correlationId(CorrelationIdResolver.resolveOrGenerate(null))
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
            throw new IllegalArgumentException("二쇰Ц ?대깽?몄뿉 orderId媛 ?놁뒿?덈떎.");
        }
        if (event.getItems() == null || event.getItems().isEmpty()) {
            throw new IllegalArgumentException("二쇰Ц ?대깽?몄뿉 ?곹뭹 ??ぉ???놁뒿?덈떎. orderId=" + event.getOrderId());
        }
        event.getItems().forEach(item -> {
            if (item.getProductId() == null) {
                throw new IllegalArgumentException("二쇰Ц ??ぉ??productId媛 ?놁뒿?덈떎. orderId=" + event.getOrderId());
            }
            toIntQuantity(item.getQuantity());
        });
    }

    private int toIntQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("二쇰Ц ?섎웾? 1 ?댁긽?댁뼱???⑸땲?? quantity=" + quantity);
        }
        if (quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("二쇰Ц ?섎웾???덈Т ?쎈땲?? quantity=" + quantity);
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





