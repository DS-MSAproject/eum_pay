package com.eum.paymentserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
/**
 * orderserver가 발행한 PaymentRequested 이벤트를 paymentserver가 소비할 때 사용하는 DTO입니다.
 * 결제 준비에 필요한 orderId, userId, amount를 전달받습니다.
 */
public class PaymentRequestedMessage {

    private String eventId;
    private String eventType;

    @JsonProperty("order_id")
    @JsonAlias({"orderId"})
    private Long orderId;

    @JsonProperty("user_id")
    @JsonAlias({"userId"})
    private Long userId;

    private Long amount;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private String producer;
    private Integer schemaVersion;

    public String processedEventId() {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        return "PaymentRequested:" + orderId + ":" + amount;
    }
}
