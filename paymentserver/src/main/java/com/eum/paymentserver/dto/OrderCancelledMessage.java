package com.eum.paymentserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * orderserver가 주문 취소를 확정했을 때 paymentserver가 보상 취소/환불을 판단하기 위해 소비하는 이벤트 DTO입니다.
 */
public class OrderCancelledMessage {

    private String eventId;

    private String eventType;

    @JsonAlias({"order_id"})
    private Long orderId;

    @JsonAlias({"user_id"})
    private Long userId;

    private String reason;
    private String correlationId;

    private LocalDateTime occurredAt;

    public String processedEventId() {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        return "OrderCancelled:" + orderId + ":" + reason;
    }
}
