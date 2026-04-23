package com.eum.inventoryserver.controller;

import com.eum.inventoryserver.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // 재고 조회
    @GetMapping("/{productId}")
    public int getStock(@PathVariable Long productId,
                        @RequestParam(required = false) Long optionId) {
        return inventoryService.getStockQuantity(productId, optionId);
    }

    // 재고 차감
    @PostMapping("/{productId}/decrease")
    public void decreaseStock(@PathVariable Long productId,
                              @RequestParam(required = false) Long optionId,
                              @RequestParam int quantity) {
        inventoryService.decreaseStock(productId, optionId, quantity);
    }

    // 재고 복구
    @PostMapping("/{productId}/increase")
    public void increaseStock(@PathVariable Long productId, @RequestParam int quantity) {
        // 기존 increaseStock 활용 (optionId는 null로 전달)
        inventoryService.increaseStock(productId, null, quantity);
    }

    // 옵션별 재고 복구 및 초기 등록
    @PostMapping("/{productId}/options/{optionId}/increase")
    public void increaseOptionStock(
            @PathVariable Long productId,
            @PathVariable Long optionId,
            @RequestParam int quantity) {
        // InventoryService에 상품ID와 옵션ID를 같이 처리하는 로직을 호출하세요
        inventoryService.increaseOptionStock(productId, optionId, quantity);
    }

    @PostMapping("/batch")
    public Map<Long, Integer> getStocks(@RequestBody List<Long> productIds) {
        return inventoryService.getStockMap(productIds);
    }


}
