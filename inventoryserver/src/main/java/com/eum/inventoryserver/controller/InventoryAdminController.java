package com.eum.inventoryserver.controller;

import com.eum.inventoryserver.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public class InventoryAdminController {

    private final InventoryService inventoryService;

    @PatchMapping("/products/{productId}/options/{optionId}")
    public void updateOptionStock(
            @PathVariable Long productId,
            @PathVariable Long optionId,
            @RequestParam int quantity
    ) {
        inventoryService.replaceStock(productId, optionId, quantity);
    }
}
