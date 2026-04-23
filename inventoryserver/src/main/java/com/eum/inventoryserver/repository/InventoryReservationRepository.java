package com.eum.inventoryserver.repository;

import com.eum.inventoryserver.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 주문 ID 기준으로 재고 예약 상태를 조회하기 위한 repository입니다.
 */
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByOrderId(Long orderId);
}
