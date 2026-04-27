package com.eum.paymentserver.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 프론트가 SSE로 결제 결과를 실시간 구독할 때 사용하는 이벤트 DTO입니다.
 * 주문 기준으로 결제 완료/실패 상태와 화면 표시용 메시지를 함께 내려줍니다.
 */
public class PaymentStatusSseEvent {
    private Long orderId;
    private String paymentId;
    private String status;
    private String message;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime approvedAt;
    private LocalDateTime failedAt;
}
