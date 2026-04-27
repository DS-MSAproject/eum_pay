package com.eum.inventoryserver.message.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결제 실패 또는 결제 취소로 재고 예약이 해제된 결과를 orderserver에 알릴 때 쓰는 payload입니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleaseResult {

    @JsonProperty("order_id")
    private Long orderId;

    private boolean success;
    private String reason;
}
