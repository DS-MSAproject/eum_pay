package com.eum.inventoryserver.message.payment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결제 완료 후 취소/환불 상태를 inventoryserver에 알릴 때 쓰는 이벤트 payload입니다.
 *
 * cancelled/canceled 상태만 재고 복구 대상으로 보고, 그 외 상태는 처리 완료로만 기록합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancelStatusEvent {

    private String eventId;

    @JsonProperty("order_id")
    @JsonAlias({"orderId", "merchant_uid"})
    private Long orderId;

    private Integer cancelAmount;
    private Integer amount;
    private Integer remainingBalance;
    private String status;
    @JsonAlias({"correlation_id", "correlationId"})
    private String correlationId;

    public String processedEventId() {
        return eventId != null ? eventId : "PAYMENT_CANCEL_STATUS:" + orderId + ":" + status + ":" + cancelAmount;
    }

    public boolean isCancelled() {
        return "cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status);
    }
}
