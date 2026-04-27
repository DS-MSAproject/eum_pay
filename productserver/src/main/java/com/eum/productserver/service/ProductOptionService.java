package com.eum.productserver.service;

import com.eum.productserver.client.InventoryStockClient;
import com.eum.productserver.dto.inventory.InventoryStockRequest;
import com.eum.productserver.dto.inventory.InventoryStockResponse;
import com.eum.productserver.dto.response.ResProductOptionDto;
import com.eum.productserver.dto.response.ProductStockInfo;
import com.eum.productserver.entity.ProductOption;
import com.eum.productserver.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductOptionService {

    private static final long NO_OPTION_ID = 0L;

    private final ProductOptionRepository productOptionRepository;
    private final InventoryStockClient inventoryStockClient;

    // 특정 상품의 모든 옵션 조회
    public List<ResProductOptionDto> findOptionsByProductId(Long productId) {
        // 1. 리포지토리를 통해 해당 상품에 속한 옵션 리스트를 먼저 가져옵니다.
        List<ProductOption> options = productOptionRepository.findByProduct_ProductId(productId);
        Map<Long, ProductStockInfo> stocksByOptionId = fetchOptionStocks(productId);

        // 2. 가져온 엔티티 리스트를 DTO 리스트로 변환합니다.
        return options.stream()
                .map(option -> ResProductOptionDto.fromEntity(option, stocksByOptionId.get(option.getId())))
                .toList();
    }


     // 2. 옵션 단건 상세 조회

    public ResProductOptionDto findOptionById(Long optionId) {
        ProductOption option = productOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 옵션을 찾을 수 없습니다. ID: " + optionId));
        Long productId = option.getProduct() != null ? option.getProduct().getProductId() : null;
        ProductStockInfo stock = fetchOptionStocks(productId).get(optionId);
        return ResProductOptionDto.fromEntity(option, stock);
    }

    private Map<Long, ProductStockInfo> fetchOptionStocks(Long productId) {
        if (productId == null) {
            return Map.of();
        }

        List<InventoryStockResponse> stocks = inventoryStockClient.getStocks(
                new InventoryStockRequest(List.of(productId))
        );
        if (stocks == null) {
            return Map.of();
        }

        return stocks.stream()
                .filter(stock -> stock.getProductId() != null && !isNoOptionId(stock.getOptionId()))
                .collect(Collectors.toMap(
                        InventoryStockResponse::getOptionId,
                        stock -> new ProductStockInfo(
                                stock.getProductId(),
                                stock.getOptionId(),
                                stock.getStockQuantity() != null ? stock.getStockQuantity() : 0,
                                stock.getStockStatus() != null ? stock.getStockStatus() : "SOLDOUT"
                        ),
                        (current, replacement) -> replacement
                ));
    }

    private boolean isNoOptionId(Long optionId) {
        return optionId == null || optionId == NO_OPTION_ID;
    }
}
