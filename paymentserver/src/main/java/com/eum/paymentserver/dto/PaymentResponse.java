package com.eum.paymentserver.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * paymentserver가 프론트에 결제 현재 상태를 내려줄 때 사용하는 공통 응답 DTO입니다.
 * 승인/실패/취소 결과와 최소 결제 메타데이터를 담습니다.
 */
public class PaymentResponse {
    private String paymentId;
    private Long orderId;
    private Long userId;
    private String provider;
    private String method;
    private String easyPayProvider;
    private Long amount;
    private String currency;
    private String paymentKey;
    private String status;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;
}
