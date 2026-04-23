package com.eum.productserver.client;

import com.eum.productserver.dto.inventory.InventoryStockRequest;
import com.eum.productserver.dto.inventory.InventoryStockResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * productserver가 inventoryserver의 내부 현재 재고 API를 호출하기 위한 Feign client입니다.
 *
 * 상품 목록/상세 출력 시 inventoryserver의 현재 재고를 조회할 때 사용합니다.
 */
@FeignClient(name = "dseum-inventory", contextId = "inventoryStockClient")
public interface InventoryStockClient {

    // productId 목록 기준으로 inventoryserver의 현재 상품/옵션 재고를 한 번에 조회합니다.
    @PostMapping("/internal/inventory/stocks")
    List<InventoryStockResponse> getStocks(@RequestBody InventoryStockRequest request);
}
