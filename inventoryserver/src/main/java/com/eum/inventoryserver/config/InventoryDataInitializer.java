package com.eum.inventoryserver.config;

import com.eum.inventoryserver.client.ProductSnapshotClient;
import com.eum.inventoryserver.client.dto.OptionSnapshotItem;
import com.eum.inventoryserver.client.dto.ProductSnapshotItem;
import com.eum.inventoryserver.client.dto.ProductSnapshotPage;
import com.eum.inventoryserver.repository.InventoryRepository;
import com.eum.inventoryserver.service.InventoryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDataInitializer implements ApplicationRunner {

    private static final String INVENTORY_SEED_DATA_PATH = "inventory_seed_data.json";
    private static final int SNAPSHOT_PAGE_SIZE = 500;
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_DELAY_MS = 5_000;

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;
    private final ProductSnapshotClient productSnapshotClient;

    @Override
    public void run(ApplicationArguments args) {
        if (inventoryRepository.count() > 0) {
            log.info("기존 재고 데이터가 존재하여 재고 seed 등록을 건너뜁니다.");
            return;
        }

        Map<String, Long> productIdByName = new HashMap<>();
        // key: "productName::optionName"
        Map<String, Long> optionIdByKey = new HashMap<>();

        fetchAllProductSnapshots(productIdByName, optionIdByKey);

        if (productIdByName.isEmpty()) {
            log.warn("productserver에서 상품 정보를 가져오지 못했습니다. 재고 seed 등록을 건너뜁니다.");
            return;
        }

        List<InventorySeed> seeds = inventorySeeds();
        int savedCount = 0;
        int skippedCount = 0;

        for (InventorySeed seed : seeds) {
            Long productId = productIdByName.get(seed.productName());
            if (productId == null) {
                log.warn("상품을 찾을 수 없어 건너뜁니다. productName={}", seed.productName());
                skippedCount++;
                continue;
            }

            Long optionId = null;
            if (seed.optionName() != null) {
                String key = seed.productName() + "::" + seed.optionName();
                optionId = optionIdByKey.get(key);
                if (optionId == null) {
                    log.warn("옵션을 찾을 수 없어 건너뜁니다. productName={}, optionName={}", seed.productName(), seed.optionName());
                    skippedCount++;
                    continue;
                }
            }

            inventoryService.increaseStock(productId, optionId, seed.stockQuantity());
            savedCount++;
        }

        log.info("초기 재고 seed 등록 완료. savedCount={}, skippedCount={}", savedCount, skippedCount);
    }

    private void fetchAllProductSnapshots(Map<String, Long> productIdByName, Map<String, Long> optionIdByKey) {
        long delayMs = INITIAL_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Long lastProductId = 0L;
                while (true) {
                    ProductSnapshotPage page = productSnapshotClient.getSnapshots(lastProductId, SNAPSHOT_PAGE_SIZE);
                    if (page == null || page.getItems() == null || page.getItems().isEmpty()) break;

                    for (ProductSnapshotItem item : page.getItems()) {
                        productIdByName.put(item.getProductName(), item.getProductId());
                        if (item.getOptions() != null) {
                            for (OptionSnapshotItem option : item.getOptions()) {
                                if (option.getOptionName() != null) {
                                    String key = item.getProductName() + "::" + option.getOptionName();
                                    optionIdByKey.put(key, option.getOptionId());
                                }
                            }
                        }
                    }

                    if (!page.isHasNext()) break;
                    lastProductId = page.getNextLastProductId();
                }

                if (!productIdByName.isEmpty()) {
                    log.info("productserver 상품 스냅샷 로드 완료. productCount={}", productIdByName.size());
                    return;
                }

                log.warn("[{}/{}] productserver에 상품이 없습니다. {}ms 후 재시도합니다.", attempt, MAX_RETRIES, delayMs);
            } catch (Exception e) {
                log.warn("[{}/{}] productserver 호출 실패: {}. {}ms 후 재시도합니다.", attempt, MAX_RETRIES, e.getMessage(), delayMs);
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delayMs = Math.min(delayMs * 2, 30_000);
            }
        }

        log.error("{}회 재시도 후에도 productserver 상품 정보를 가져오지 못했습니다.", MAX_RETRIES);
    }

    private List<InventorySeed> inventorySeeds() {
        ClassPathResource resource = new ClassPathResource(INVENTORY_SEED_DATA_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("재고 seed JSON 파일을 읽을 수 없습니다. path=" + INVENTORY_SEED_DATA_PATH, ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InventorySeed(
            String productName,
            String optionName,
            Integer stockQuantity
    ) {
    }
}
