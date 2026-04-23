package com.eum.paymentserver.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * prepare API 호출 후 프론트에 반환하는 응답 DTO입니다.
 * 생성된 paymentId와 Toss 위젯 초기화에 필요한 결제 표시 정보를 담습니다.
 */
public class PreparePaymentResponse {
    private String paymentId;
    private Long orderId;
    private String orderName;
    private Long amount;
    private String customerName;
    private String customerEmail;
    private String currency;
    private String status;
}
