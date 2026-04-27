package com.eum.paymentserver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 재고 차감 결과를 표현하던 레거시 이벤트 DTO입니다.
 * 현재 paymentserver의 표준 흐름에서는 직접 사용하지 않는 이전 계약용 클래스입니다.
 */
public class InventoryDeductionResultMessage {

    private Long orderId;
    private boolean success;
    private String reason;
}
