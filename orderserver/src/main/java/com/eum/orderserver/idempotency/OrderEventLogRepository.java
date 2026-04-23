package com.eum.orderserver.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderEventLogRepository extends JpaRepository<OrderEventLog, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO order_event_log (event_type, event_key, created_at)
            VALUES (:eventType, :eventKey, :createdAt)
            ON CONFLICT (event_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventType") String eventType,
                       @Param("eventKey") String eventKey,
                       @Param("createdAt") LocalDateTime createdAt);
}
