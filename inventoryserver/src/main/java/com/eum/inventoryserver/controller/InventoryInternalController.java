package com.eum.inventoryserver.controller;

import com.eum.inventoryserver.message.inventory.InventoryStockRequest;
import com.eum.inventoryserver.message.inventory.InventoryStockResponse;
import com.eum.inventoryserver.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 서버 내부 통신용 inventory API입니다.
 *
 * productserver가 상품 목록/상세 출력 시 현재 재고를 가져가기 위해 호출합니다.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j
public class InventoryInternalController {

    private final InventoryService inventoryService;

    // productserver가 FeignClient로 호출하는 내부 현재 재고 조회 API입니다.
    @PostMapping("/inventory/stocks")
    public List<InventoryStockResponse> getStocks(@RequestBody InventoryStockRequest request) {
        log.info("InventoryService.InventoryInternalController request= {}", request);

        return inventoryService.getStocks(request.getProductIds());
    }
}
