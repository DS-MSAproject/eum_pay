package com.eum.orderserver.dto.admin;

import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.domain.Orders;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class AdminOutboxPendingResponse {

    private Long id;           // Orders.id (DB PK) — retry/compensate 엔드포인트에 사용
    private Long aggregateId;  // Orders.orderId (비즈니스 ID)
    private Long userId;
    private String eventType;  // 현재 orderState 이름
    private int retryCount;    // 현재 0 고정 (별도 추적 테이블 미구현)
    private String status;     // "FAILED" | "PENDING"
    private LocalDateTime createdAt;

    private static final Set<OrderState> FAILED_STATES = Set.of(
            OrderState.INVENTORY_RESERVATION_FAILED,
            OrderState.PAYMENT_FAILED,
            OrderState.INVENTORY_DEDUCTION_FAILED,
            OrderState.INVENTORY_RELEASE_FAILED
    );

    public static AdminOutboxPendingResponse from(Orders o) {
        boolean isFailed = FAILED_STATES.contains(o.getOrderState());
        return AdminOutboxPendingResponse.builder()
                .id(o.getId())
                .aggregateId(o.getOrderId())
                .userId(o.getUserId())
                .eventType(o.getOrderState().name())
                .retryCount(0)
                .status(isFailed ? "FAILED" : "PENDING")
                .createdAt(o.getFailedAt() != null ? o.getFailedAt() : o.getTime())
                .build();
    }
}
