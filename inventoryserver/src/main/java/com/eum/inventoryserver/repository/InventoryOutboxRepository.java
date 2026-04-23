package com.eum.inventoryserver.repository;

import com.eum.inventoryserver.outbox.InventoryOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * InventoryOutbox row를 저장하는 repository입니다.
 *
 * Kafka 발행은 애플리케이션 스케줄러가 아니라 Debezium CDC connector가 담당합니다.
 */
public interface InventoryOutboxRepository extends JpaRepository<InventoryOutbox, Long> {
}
