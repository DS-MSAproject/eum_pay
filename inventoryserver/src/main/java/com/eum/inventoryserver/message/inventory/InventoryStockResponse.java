package com.eum.inventoryserver.message.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * inventoryserver가 productserver에 돌려주는 현재 재고 DTO입니다.
 *
 * optionId가 있으면 옵션 단위 재고, 0이면 상품 단위 재고를 의미합니다.
 * 내부 저장/조회에서는 상품 단위 재고를 null optionId로 유지합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockResponse {

    private Long productId;
    private Long optionId;
    private Integer stockQuantity;
    private String stockStatus;
}
