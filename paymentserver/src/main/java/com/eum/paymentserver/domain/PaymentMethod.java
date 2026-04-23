package com.eum.paymentserver.domain;

public enum PaymentMethod {
    CARD,
    EASY_PAY,
    VIRTUAL_ACCOUNT,
    TRANSFER,
    MOBILE_PHONE,
    UNKNOWN;

    public static PaymentMethod from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        try {
            return PaymentMethod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
