package com.eum.inventoryserver.dto.admin;

import com.eum.inventoryserver.entity.Inventory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminInventoryResponse {

    private Long id;
    private Long productId;
    private Long optionId;
    private int stockQuantity;

    public static AdminInventoryResponse from(Inventory inv) {
        return AdminInventoryResponse.builder()
                .id(inv.getId())
                .productId(inv.getProductId())
                .optionId(inv.getOptionId())
                .stockQuantity(inv.getStockQuantity())
                .build();
    }
}
