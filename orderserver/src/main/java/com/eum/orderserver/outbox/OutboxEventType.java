package com.eum.orderserver.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxEventType {
    ORDER_CHECKED_OUT("OrderCheckedOut"),
    ORDER_COMPLETED("OrderCompleted"),
    ORDER_CANCELLED("OrderCancelled");

    private final String eventName;
}