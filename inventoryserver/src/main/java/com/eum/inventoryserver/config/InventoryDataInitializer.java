package com.eum.inventoryserver.config;

import com.eum.inventoryserver.repository.InventoryRepository;
import com.eum.inventoryserver.service.InventoryService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDataInitializer implements ApplicationRunner {

    private static final String INVENTORY_SEED_DATA_PATH = "inventory_seed_data.json";

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (inventoryRepository.count() > 0) {
            log.info("기존 재고 데이터가 존재하여 재고 seed 등록을 건너뜁니다.");
            return;
        }

        List<InventorySeed> inventorySeeds = inventorySeeds();
        inventorySeeds.forEach(this::validateInventorySeed);
        inventorySeeds.forEach(seed ->
                inventoryService.increaseStock(seed.productId(), seed.optionId(), seed.stockQuantity())
        );

        log.info("초기 재고 seed 등록 완료. savedCount={}", inventorySeeds.size());
    }

    private List<InventorySeed> inventorySeeds() {
        ClassPathResource resource = new ClassPathResource(INVENTORY_SEED_DATA_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("재고 seed JSON 파일을 읽을 수 없습니다. path=" + INVENTORY_SEED_DATA_PATH, ex);
        }
    }

    private void validateInventorySeed(InventorySeed seed) {
        if (seed.productId() == null) {
            throw new IllegalStateException("재고 seed 상품 ID가 비어 있습니다. productName=" + seed.productName());
        }
        if (seed.stockQuantity() == null || seed.stockQuantity() < 0) {
            throw new IllegalStateException("재고 seed 수량은 0 이상이어야 합니다. productId=" + seed.productId()
                    + ", optionId=" + seed.optionId()
                    + ", stockQuantity=" + seed.stockQuantity());
        }
    }

    private record InventorySeed(
            Long productId,
            Long optionId,
            Integer stockQuantity,
            @JsonProperty("productName") String productName,
            @JsonProperty("optionName") String optionName
    ) {
    }
}
