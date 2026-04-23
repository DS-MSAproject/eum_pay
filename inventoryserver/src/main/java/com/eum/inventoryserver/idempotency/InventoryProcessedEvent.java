package com.eum.inventoryserver.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * inventoryserver가 이미 처리한 외부 이벤트를 기록하는 멱등성 엔티티입니다.
 *
 * Kafka/outbox 특성상 같은 이벤트가 다시 전달될 수 있으므로 eventId를 unique로 저장해
 * 재고 차감, 복구, 삭제 같은 부작용이 중복 실행되지 않게 합니다.
 */
@Getter
@Entity
@Table(name = "inventory_processed_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 150)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public InventoryProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }
}
