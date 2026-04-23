package com.eum.inventoryserver.message.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * productserver가 현재 재고를 조회하기 위해 inventoryserver에 보내는 내부 요청 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockRequest {

    private List<Long> productIds;
}
