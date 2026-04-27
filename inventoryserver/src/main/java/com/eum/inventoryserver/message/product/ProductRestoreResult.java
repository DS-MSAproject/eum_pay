package com.eum.inventoryserver.message.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결제 취소/환불 성공 후 product restore 결과를 orderserver에 전달하기 위한 payload입니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRestoreResult {

    @JsonProperty("order_id")
    private Long orderId;

    private boolean success;
    private String reason;
}
