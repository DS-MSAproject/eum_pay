package com.eum.paymentserver.dto.admin;

import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentState;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminPaymentResponse {

    private Long id;
    private String paymentId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private String currency;
    private String method;
    private String provider;
    private PaymentState status;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime approvedAt;
    private LocalDateTime failedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;

    public static AdminPaymentResponse from(Payment p) {
        return AdminPaymentResponse.builder()
                .id(p.getId())
                .paymentId(p.getPaymentId())
                .orderId(p.getOrderId())
                .userId(p.getUserId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .method(p.getMethod() != null ? p.getMethod().name() : null)
                .provider(p.getProvider() != null ? p.getProvider().name() : null)
                .status(p.getStatus())
                .failureCode(p.getFailureCode())
                .failureMessage(p.getFailureMessage())
                .approvedAt(p.getApprovedAt())
                .failedAt(p.getFailedAt())
                .canceledAt(p.getCanceledAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
