package com.eum.inventoryserver.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
