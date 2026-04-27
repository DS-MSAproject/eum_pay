package com.eum.orderserver.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderState {

    ORDER_CHECKED_OUT("주문체크아웃"),
    INVENTORY_RESERVED("재고예약완료"),
    INVENTORY_RESERVATION_FAILED("재고예약실패"),
    PAYMENT_COMPLETED("결제완료"),
    PAYMENT_FAILED("결제실패"),
    INVENTORY_DEDUCTION_FAILED("재고차감실패"),
    INVENTORY_RELEASED("재고예약해제완료"),
    INVENTORY_RELEASE_FAILED("재고예약해제실패"),
    ORDER_COMPLETED("주문완료"),
    ORDER_CANCELLED("주문취소");

    private final String state;
}