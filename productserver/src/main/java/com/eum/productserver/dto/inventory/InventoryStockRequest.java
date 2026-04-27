package com.eum.productserver.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * productserver가 FeignClient로 inventoryserver 현재 재고를 요청할 때 쓰는 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockRequest {

    private List<Long> productIds;
}
