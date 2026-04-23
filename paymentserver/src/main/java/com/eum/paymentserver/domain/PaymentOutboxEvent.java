package com.eum.paymentserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 결제 결과 이벤트를 외부 서버로 전파하기 위한 Outbox 엔티티입니다.
 * Debezium CDC가 이 테이블을 읽어 PaymentCompleted, PaymentFailed 토픽으로 발행합니다.
 */
public class PaymentOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder
    private PaymentOutboxEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            OutboxEventStatus status
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
    }

    public static PaymentOutboxEvent pending(String aggregateId, String eventType, String payload) {
        return PaymentOutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .build();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
