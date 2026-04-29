package com.eum.orderserver.dto.admin;

import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.domain.Orders;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminInconsistencyResponse {

    private Long orderId;
    private OrderState orderState;
    private String issue;
    private LocalDateTime detectedAt;

    public static AdminInconsistencyResponse of(Orders o, String issue) {
        return AdminInconsistencyResponse.builder()
                .orderId(o.getOrderId())
                .orderState(o.getOrderState())
                .issue(issue)
                .detectedAt(o.getFailedAt() != null ? o.getFailedAt() : o.getTime())
                .build();
    }
}
