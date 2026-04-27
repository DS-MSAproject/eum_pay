package com.eum.paymentserver.domain;

public enum PaymentState {
    READY,
    APPROVAL_REQUESTED,
    APPROVED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELED,
    CANCEL_FAILED
}
