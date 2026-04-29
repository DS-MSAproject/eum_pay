package com.eum.orderserver.dto.admin;

import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.domain.Orders;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminOrderResponse {

    private Long id;
    private Long orderId;
    private Long userId;
    private String userName;
    private Long totalAmount;
    private Long paidAmount;
    private String paymentMethod;
    private OrderState orderState;
    private String failedReason;
    private LocalDateTime failedAt;
    private LocalDateTime createdAt;

    public static AdminOrderResponse from(Orders o) {
        return AdminOrderResponse.builder()
                .id(o.getId())
                .orderId(o.getOrderId())
                .userId(o.getUserId())
                .userName(o.getUserName())
                .totalAmount(o.getAmount())
                .paidAmount(o.getPaidAmount())
                .paymentMethod(o.getPaymentMethod())
                .orderState(o.getOrderState())
                .failedReason(o.getFailedReason())
                .failedAt(o.getFailedAt())
                .createdAt(o.getTime())
                .build();
    }
}
