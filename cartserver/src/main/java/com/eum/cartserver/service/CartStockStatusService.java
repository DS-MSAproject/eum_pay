package com.eum.cartserver.service;

import com.eum.cartserver.client.InventoryStockClient;
import com.eum.cartserver.client.dto.InventoryStockRequest;
import com.eum.cartserver.client.dto.InventoryStockResponse;
import com.eum.cartserver.dto.CartItemResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartStockStatusService {

    private static final long NO_OPTION_ID = 0L;
    private static final String SOLD_OUT = "SOLDOUT";

    private final InventoryStockClient inventoryStockClient;

    public List<CartItemResponse> enrichSoldOutStatus(List<CartItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Set<Long> productIds = items.stream()
                .map(CartItemResponse::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (productIds.isEmpty()) {
            return items.stream()
                    .map(item -> item.toBuilder().soldOut(false).build())
                    .toList();
        }

        Map<StockKey, InventoryStockResponse> stockByKey = loadStocks(productIds);

        return items.stream()
                .map(item -> item.toBuilder()
                        .soldOut(isSoldOut(item, stockByKey))
                        .build())
                .toList();
    }

    private Map<StockKey, InventoryStockResponse> loadStocks(Set<Long> productIds) {
        try {
            List<InventoryStockResponse> stocks = inventoryStockClient.getStocks(
                    new InventoryStockRequest(List.copyOf(productIds))
            );

            if (stocks == null || stocks.isEmpty()) {
                return Map.of();
            }

            return stocks.stream()
                    .filter(stock -> stock.getProductId() != null)
                    .collect(Collectors.toMap(
                            stock -> new StockKey(stock.getProductId(), normalizeOptionId(stock.getOptionId())),
                            Function.identity(),
                            (current, replacement) -> replacement
                    ));
        } catch (FeignException ex) {
            log.warn("cart stock lookup failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    private boolean isSoldOut(CartItemResponse item, Map<StockKey, InventoryStockResponse> stockByKey) {
        InventoryStockResponse stock = stockByKey.get(
                new StockKey(item.getProductId(), normalizeOptionId(item.getOptionId()))
        );

        if (stock == null) {
            return false;
        }

        if (stock.getStockQuantity() != null && stock.getStockQuantity() <= 0) {
            return true;
        }

        return SOLD_OUT.equalsIgnoreCase(stock.getStockStatus());
    }

    private long normalizeOptionId(Long optionId) {
        return optionId == null ? NO_OPTION_ID : optionId;
    }

    private record StockKey(Long productId, Long optionId) {
    }
}
