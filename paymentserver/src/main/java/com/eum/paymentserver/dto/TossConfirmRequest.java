package com.eum.paymentserver.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * paymentserver가 Toss 승인 API를 호출할 때 사용하는 내부 요청 DTO입니다.
 * Toss 승인에 필요한 paymentKey, orderId, amount를 담습니다.
 */
public class TossConfirmRequest {

    private String paymentKey;
    private String orderId;
    private Long amount;
}
