package com.eum.inventoryserver.entity;

public enum InventoryReservationStatus {
    // 주문 재고 예약 성공: 이 시점에는 가용 재고를 차감하지 않고 가능 여부만 확인합니다.
    RESERVED,
    // 재고 부족 등으로 예약이 실패한 상태입니다.
    REJECTED,
    // 결제 성공 후 실제 재고 차감까지 완료되어 최종 판매로 확정한 상태입니다.
    CONFIRMED,
    // 결제 실패 또는 결제 취소 이벤트 수신 후 예약이 해제된 상태입니다.
    RELEASED
}
