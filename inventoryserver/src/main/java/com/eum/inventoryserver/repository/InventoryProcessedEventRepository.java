package com.eum.inventoryserver.repository;

import com.eum.inventoryserver.idempotency.InventoryProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * inventoryserver가 이미 처리한 외부 이벤트인지 확인하는 멱등성 repository입니다.
 */
public interface InventoryProcessedEventRepository extends JpaRepository<InventoryProcessedEvent, Long> {
    boolean existsByEventId(String eventId);
}
