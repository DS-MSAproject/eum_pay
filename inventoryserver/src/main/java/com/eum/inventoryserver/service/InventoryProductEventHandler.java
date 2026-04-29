package com.eum.inventoryserver.service;

import com.eum.inventoryserver.entity.Inventory;
import com.eum.inventoryserver.message.product.ProductCreatedEvent;
import com.eum.inventoryserver.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryProductEventHandler {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public void handleProductCreated(ProductCreatedEvent event) {
        log.info("[Inventory] PRODUCT_CREATED 이벤트 수신: productId={}", event.getProductId());

        if (event.getOptions() == null || event.getOptions().isEmpty()) {
            // 옵션 없는 상품 — optionId = 0 (또는 null) 로 단일 재고 레코드 생성
            createInventoryIfAbsent(event.getProductId(), null, event.getInitialStock());
            return;
        }

        for (ProductCreatedEvent.OptionInfo option : event.getOptions()) {
            Long optionId = (option.getOptionId() == null || option.getOptionId() == 0L)
                    ? null : option.getOptionId();
            createInventoryIfAbsent(event.getProductId(), optionId, event.getInitialStock());
        }
    }

    private void createInventoryIfAbsent(Long productId, Long optionId, int initialStock) {
        boolean exists = inventoryRepository.findByProductIdAndOptionId(productId, optionId).isPresent();
        if (exists) {
            log.debug("[Inventory] 재고 레코드 이미 존재: productId={}, optionId={}", productId, optionId);
            return;
        }
        Inventory inventory = Inventory.builder()
                .productId(productId)
                .optionId(optionId)
                .stockQuantity(Math.max(0, initialStock))
                .build();
        inventoryRepository.save(inventory);
        log.info("[Inventory] 재고 레코드 생성: productId={}, optionId={}, stock={}", productId, optionId, initialStock);
    }
}
