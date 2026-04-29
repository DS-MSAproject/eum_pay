package com.eum.paymentserver.dto.admin;

import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentAttempt;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminIdempotencyViolationResponse {

    private String paymentId;
    private Long orderId;
    private int duplicateCount;          // CONFIRM 시도 횟수
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private boolean resolved;            // 최종 상태가 APPROVED면 resolved

    public static AdminIdempotencyViolationResponse of(Payment p, List<PaymentAttempt> attempts) {
        LocalDateTime first = attempts.isEmpty() ? p.getCreatedAt() : attempts.get(0).getCreatedAt();
        LocalDateTime last  = attempts.isEmpty() ? p.getCreatedAt() : attempts.get(attempts.size() - 1).getCreatedAt();
        return AdminIdempotencyViolationResponse.builder()
                .paymentId(p.getPaymentId())
                .orderId(p.getOrderId())
                .duplicateCount(attempts.size())
                .firstOccurrence(first)
                .lastOccurrence(last)
                .resolved(p.getApprovedAt() != null)
                .build();
    }
}
