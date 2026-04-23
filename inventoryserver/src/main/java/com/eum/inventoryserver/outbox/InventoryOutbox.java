package com.eum.inventoryserver.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * inventoryserver 트랜잭션 안에서 발행할 이벤트를 먼저 저장하는 outbox 엔티티입니다.
 *
 * 재고 변경과 이벤트 저장을 같은 DB 트랜잭션에 묶고, Debezium CDC connector가 insert를 감지해 Kafka로 전송합니다.
 */
@Getter
@Entity
@Table(
        name = "inventory_outbox",
        indexes = {
                @Index(name = "idx_inventory_outbox_event_id", columnList = "event_id", unique = true)
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public InventoryOutbox(String eventId, String aggregateType, Long aggregateId, String eventType, String topic, String payload) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }
}
