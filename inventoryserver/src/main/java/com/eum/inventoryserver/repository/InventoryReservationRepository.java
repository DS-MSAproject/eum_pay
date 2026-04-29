package com.eum.inventoryserver.repository;

import com.eum.inventoryserver.entity.InventoryReservation;
import com.eum.inventoryserver.entity.InventoryReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 ID 기준으로 재고 예약 상태를 조회하기 위한 repository입니다.
 */
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByOrderId(Long orderId);

    List<InventoryReservation> findAllByStatusAndCreatedAtBefore(
            InventoryReservationStatus status, LocalDateTime before);

    @Query("SELECT DISTINCT r FROM InventoryReservation r JOIN r.items i " +
           "WHERE i.productId = :productId ORDER BY r.createdAt DESC")
    List<InventoryReservation> findAllByProductId(@Param("productId") Long productId);
}
