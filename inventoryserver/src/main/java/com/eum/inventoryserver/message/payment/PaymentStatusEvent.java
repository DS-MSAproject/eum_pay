package com.eum.inventoryserver.message.payment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * paymentserver가 결제 완료/실패 상태를 inventoryserver에 알릴 때 쓰는 이벤트 payload입니다.
 *
 * 결제 완료는 재고 차감과 예약 확정으로, 결제 실패는 InventoryReleased 이벤트 체이닝으로 이어집니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusEvent {

    private String eventId;

    @JsonProperty("order_id")
    @JsonAlias({"orderId", "merchant_uid"})
    private Long orderId;

    @JsonProperty("paymentStatus")
    @JsonAlias({"paymentstatus", "payment_status", "status"})
    private String paymentStatus;

    @JsonAlias({"correlation_id", "correlationId"})
    private String correlationId;

    public String processedEventId() {
        return eventId != null ? eventId : "PAYMENT_STATUS:" + orderId + ":" + paymentStatus;
    }

    public boolean isCompleted() {
        return "PAYCOMPLETE".equalsIgnoreCase(paymentStatus) || "paid".equalsIgnoreCase(paymentStatus);
    }

    public boolean isFailed() {
        return "PAYFAIL".equalsIgnoreCase(paymentStatus) || "failed".equalsIgnoreCase(paymentStatus);
    }
}
