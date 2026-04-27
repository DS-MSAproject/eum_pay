package com.eum.orderserver.message.payment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderEvent {

    private String eventId;
    private String eventType;

    @JsonProperty("order_id")
    @JsonAlias({"orderId", "merchant_uid"})
    private Long orderId;

    @JsonProperty("user_id")
    @JsonAlias({"userId"})
    private Long userId;

    private Long amount;

    @JsonAlias({"payment_method", "paymentMethod", "method"})
    private String paymentMethod;

    @JsonAlias({"paid_amount", "paidAmount"})
    private Long paidAmount;

    @JsonAlias({"failure_code"})
    private String failureCode;

    @JsonAlias({"failure_reason"})
    private String failureReason;

    @JsonAlias({"failed_at"})
    private LocalDateTime failedAt;

    private String reason;

    public String getFailureReason() {
        if (failureReason != null && !failureReason.isBlank()) {
            return failureReason;
        }
        return reason;
    }

    public String processedEventId(String fallbackType) {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        return fallbackType + ":" + orderId;
    }
}