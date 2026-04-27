package com.eum.productserver.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * inventoryserver 내부 API 응답을 productserver에서 받기 위한 현재 재고 DTO입니다.
 * optionId가 null 또는 0이면 상품 단위 재고로 처리합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockResponse {

    private Long productId;
    private Long optionId;
    private Integer stockQuantity;
    private String stockStatus;
}
