package com.eum.inventoryserver.message.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결제 성공 후 inventoryserver가 주문 재고 확정 결과를 orderserver에 알릴 때 쓰는 payload입니다.
 *
 * 현재 구조에서는 결제 완료 시점의 실제 재고 차감과 예약 상태 확정 성공/실패를 의미합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductionResult {

    @JsonProperty("order_id")
    private Long orderId;

    private String correlationId;
    private boolean success;
    private String reason;
}
