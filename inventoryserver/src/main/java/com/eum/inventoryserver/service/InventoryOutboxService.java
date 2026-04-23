package com.eum.inventoryserver.service;

import com.eum.inventoryserver.outbox.InventoryOutbox;
import com.eum.inventoryserver.repository.InventoryOutboxRepository;
import com.eum.inventoryserver.message.inventory.InventoryDeductionResult;
import com.eum.inventoryserver.message.inventory.InventoryReleaseResult;
import com.eum.inventoryserver.message.order.OrderCheckedOutEvent;
import com.eum.inventoryserver.message.payment.PaymentRequestedEvent;
import com.eum.inventoryserver.message.product.ProductReservationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * inventoryserver가 외부로 보낼 이벤트를 InventoryOutbox row로 저장하는 서비스입니다.
 *
 * 여기서는 Kafka에 직접 보내지 않고 payload를 직렬화해 outbox 테이블에 저장합니다.
 * 이후 Debezium CDC connector가 inventory_outbox insert를 감지하고 topic 컬럼 기준으로 Kafka에 발행합니다.
 */
@Service
@RequiredArgsConstructor
public class InventoryOutboxService {

    private static final String ORDER_AGGREGATE_TYPE = "ORDER";
    private static final String INVENTORY_RESERVED = "InventoryReserved";
    private static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed";
    private static final String INVENTORY_RELEASED = "InventoryReleased";
    private static final String INVENTORY_RELEASE_FAILED = "InventoryReleaseFailed";
    private static final String INVENTORY_DEDUCTED = "InventoryDeducted";
    private static final String INVENTORY_DEDUCTION_FAILED = "InventoryDeductionFailed";
    private static final String PAYMENT_REQUESTED = "PaymentRequested";
    private static final String PRODUCER = "inventoryserver";

    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueReservationResult(ProductReservationResult event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventType = event.isSuccess() ? INVENTORY_RESERVED : INVENTORY_RESERVATION_FAILED;
            saveOrderOutbox(event.getOrderId(), eventType, eventType, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("재고 예약 결과 이벤트 아웃박스 직렬화 실패", e);
        }
    }

    @Transactional
    public void enqueuePaymentRequested(OrderCheckedOutEvent event) {
        String eventId = UUID.randomUUID().toString();
        PaymentRequestedEvent paymentRequested = PaymentRequestedEvent.builder()
                .eventId(eventId)
                .eventType(PAYMENT_REQUESTED)
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.paymentAmount())
                .correlationId(event.getCorrelationId())
                .causationId(event.processedEventId())
                .occurredAt(LocalDateTime.now())
                .producer(PRODUCER)
                .schemaVersion(event.getSchemaVersion() != null ? event.getSchemaVersion() : 1)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(paymentRequested);
            saveOrderOutbox(eventId, event.getOrderId(), PAYMENT_REQUESTED, PAYMENT_REQUESTED, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결제 요청 이벤트 아웃박스 직렬화 실패", e);
        }
    }

    @Transactional
    public void enqueueReleaseResult(InventoryReleaseResult event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventType = event.isSuccess() ? INVENTORY_RELEASED : INVENTORY_RELEASE_FAILED;
            saveOrderOutbox(event.getOrderId(), eventType, eventType, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("재고 해제 결과 이벤트 아웃박스 직렬화 실패", e);
        }
    }

    @Transactional
    public void enqueueDeductionResult(InventoryDeductionResult event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventType = event.isSuccess() ? INVENTORY_DEDUCTED : INVENTORY_DEDUCTION_FAILED;
            saveOrderOutbox(event.getOrderId(), eventType, eventType, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("재고 차감 확정 결과 이벤트 아웃박스 직렬화 실패", e);
        }
    }

    private void saveOrderOutbox(Long orderId, String eventType, String topic, String payload) {
        saveOrderOutbox(UUID.randomUUID().toString(), orderId, eventType, topic, payload);
    }

    private void saveOrderOutbox(String eventId, Long orderId, String eventType, String topic, String payload) {
        outboxRepository.save(InventoryOutbox.builder()
                .eventId(eventId)
                .aggregateType(ORDER_AGGREGATE_TYPE)
                .aggregateId(orderId)
                .eventType(eventType)
                .topic(topic)
                .payload(payload)
                .build());
    }
}
