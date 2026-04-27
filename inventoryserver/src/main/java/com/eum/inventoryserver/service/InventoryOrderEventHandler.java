package com.eum.inventoryserver.service;

import com.eum.common.correlation.Correlated;
import com.eum.common.correlation.CorrelationIdSource;
import com.eum.inventoryserver.idempotency.InventoryProcessedEvent;
import com.eum.inventoryserver.message.inventory.InventoryDeductionResult;
import com.eum.inventoryserver.message.inventory.InventoryReleaseResult;
import com.eum.inventoryserver.message.order.OrderCheckedOutEvent;
import com.eum.inventoryserver.message.payment.PaymentCancelStatusEvent;
import com.eum.inventoryserver.message.payment.PaymentStatusEvent;
import com.eum.inventoryserver.message.product.ProductReservationResult;
import com.eum.inventoryserver.message.product.ProductRestoreResult;
import com.eum.inventoryserver.repository.InventoryProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문/결제 이벤트를 재고 예약 → 차감 → 복구 흐름으로 연결하는 이벤트 핸들러.
 *
 * 각 핸들러 메서드는 @Transactional로 묶여 재고 상태 변경과 Outbox 적재가
 * 원자적으로 처리된다. 인프라 장애 시 트랜잭션이 롤백되어 Kafka 재처리로 복구된다.
 *
 * 분산 트랜잭션은 Outbox Pattern 기반 이벤트 체이닝으로만 처리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Correlated
public class InventoryOrderEventHandler {

    private static final String PAYMENT_COMPLETED_STATUS = "PAYCOMPLETE";
    private static final String PAYMENT_FAILED_STATUS = "PAYFAIL";

    private final InventoryService inventoryService;
    private final InventoryOutboxService inventoryOutboxService;
    private final InventoryProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleOrderCheckedOut(@CorrelationIdSource OrderCheckedOutEvent event) {
        String eventId = event.processedEventId();
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("중복 주문 재고 예약 이벤트 무시: {}", eventId);
            return;
        }

        // reserveOrderStock은 비즈니스 실패(재고 부족 등)를 예외가 아닌 결과 객체로 반환
        // → 인프라 장애만 예외로 전파되어 TX 롤백 후 Kafka 재처리
        ProductReservationResult result = inventoryService.reserveOrderStock(event);
        inventoryOutboxService.enqueueReservationResult(result);
        if (result.isSuccess()) {
            inventoryOutboxService.enqueuePaymentRequested(event);
        }
        markProcessed(eventId, "ORDER_CHECKED_OUT");
    }

    @Transactional
    public void handlePaymentCompletedTopic(@CorrelationIdSource PaymentStatusEvent event) {
        event.setPaymentStatus(PAYMENT_COMPLETED_STATUS);
        String eventId = canonicalPaymentEventId(event.getOrderId(), PAYMENT_COMPLETED_STATUS);
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("중복 결제 완료 이벤트 무시: {}", eventId);
            return;
        }

        InventoryDeductionResult result = inventoryService.tryConfirmReservedStock(
                event.getOrderId(),
                event.getCorrelationId()
        );
        inventoryOutboxService.enqueueDeductionResult(result);
        markProcessed(eventId, "PAYMENT_COMPLETED");
    }

    @Transactional
    public void handlePaymentFailedTopic(@CorrelationIdSource PaymentStatusEvent event) {
        event.setPaymentStatus(PAYMENT_FAILED_STATUS);
        String eventId = canonicalPaymentEventId(event.getOrderId(), PAYMENT_FAILED_STATUS);
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("중복 결제 실패 이벤트 무시: {}", eventId);
            return;
        }

        ProductRestoreResult result = inventoryService.releaseReservedStock(
                event.getOrderId(),
                "PAYMENT_FAILED",
                event.getCorrelationId()
        );
        inventoryOutboxService.enqueueReleaseResult(InventoryReleaseResult.builder()
                .orderId(result.getOrderId())
                .correlationId(result.getCorrelationId())
                .success(result.isSuccess())
                .reason(result.getReason())
                .build());
        markProcessed(eventId, "PAYMENT_FAILED");
    }

    @Transactional
    public void handlePaymentCancelStatus(@CorrelationIdSource PaymentCancelStatusEvent event) {
        String eventId = event.processedEventId();
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("중복 결제 취소 상태 이벤트 무시: {}", eventId);
            return;
        }

        if (!event.isCancelled()) {
            log.info("재고 복구 대상이 아닌 결제 취소 이벤트 무시: orderId={}, status={}",
                    event.getOrderId(), event.getStatus());
            markProcessed(eventId, "PAYMENT_CANCEL_STATUS_IGNORED");
            return;
        }

        ProductRestoreResult result = inventoryService.releaseReservedStock(
                event.getOrderId(),
                "PAYMENT_CANCELLED",
                event.getCorrelationId()
        );
        inventoryOutboxService.enqueueReleaseResult(InventoryReleaseResult.builder()
                .orderId(result.getOrderId())
                .correlationId(result.getCorrelationId())
                .success(result.isSuccess())
                .reason(result.getReason())
                .build());
        markProcessed(eventId, "PAYMENT_CANCELLED");
    }

    private void markProcessed(String eventId, String eventType) {
        processedEventRepository.save(new InventoryProcessedEvent(eventId, eventType));
    }

    private String canonicalPaymentEventId(Long orderId, String paymentStatus) {
        return "PAYMENT_STATUS:" + orderId + ":" + paymentStatus;
    }
}
