package com.eum.inventoryserver.message.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 재고 예약 성공 후 paymentserver에 결제 준비를 요청하는 payload입니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestedEvent {

    private String eventId;
    private String eventType;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("user_id")
    private Long userId;

    private Long amount;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private String producer;
    private Integer schemaVersion;
}
