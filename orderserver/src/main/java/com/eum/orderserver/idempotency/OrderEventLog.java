package com.eum.orderserver.idempotency;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_event_log", indexes = {
        @Index(name = "idx_order_event_log_type", columnList = "event_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_event_key", columnNames = "event_key")
})
@Getter
@Setter
@NoArgsConstructor
public class OrderEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OrderEventLog(String eventType, String eventKey) {
        this.eventType = eventType;
        this.eventKey = eventKey;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
