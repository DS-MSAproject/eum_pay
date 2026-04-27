package com.eum.orderserver.service;

import com.eum.common.correlation.Correlated;
import com.eum.common.correlation.CorrelationIdSource;
import com.eum.orderserver.idempotency.IdempotencyService;
import com.eum.orderserver.message.inventory.InventoryDeductionEvent;
import com.eum.orderserver.message.inventory.InventoryReleaseEvent;
import com.eum.orderserver.message.inventory.InventoryReservationEvent;
import com.eum.orderserver.message.payment.PaymentOrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Correlated
public class OrderEventProcessor {

    private final IdempotencyService idempotencyService;
    private final OrderService orderService;

    @Transactional
    public void processInventoryReserved(@CorrelationIdSource InventoryReservationEvent event) {
        String eventKey = event.processedEventId("INVENTORY_RESERVED");
        if (!idempotencyService.tryRegister("INVENTORY_RESERVED", eventKey)) {
            log.info("중복 재고 예약 완료 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handleInventoryReserved(event);
    }

    @Transactional
    public void processInventoryReservationFailed(@CorrelationIdSource InventoryReservationEvent event) {
        String eventKey = event.processedEventId("INVENTORY_RESERVATION_FAILED");
        if (!idempotencyService.tryRegister("INVENTORY_RESERVATION_FAILED", eventKey)) {
            log.info("중복 재고 예약 실패 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handleInventoryReservationFailed(event);
    }

    @Transactional
    public void processPaymentCompleted(@CorrelationIdSource PaymentOrderEvent event) {
        String eventKey = event.processedEventId("PAYMENT_COMPLETED");
        if (!idempotencyService.tryRegister("PAYMENT_COMPLETED", eventKey)) {
            log.info("중복 결제 완료 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handlePaymentCompleted(event);
    }

    @Transactional
    public void processPaymentFailed(@CorrelationIdSource PaymentOrderEvent event) {
        String eventKey = event.processedEventId("PAYMENT_FAILED");
        if (!idempotencyService.tryRegister("PAYMENT_FAILED", eventKey)) {
            log.info("중복 결제 실패 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handlePaymentFailed(event);
    }

    @Transactional
    public void processInventoryDeducted(@CorrelationIdSource InventoryDeductionEvent event) {
        String eventKey = event.processedEventId("INVENTORY_DEDUCTED");
        if (!idempotencyService.tryRegister("INVENTORY_DEDUCTED", eventKey)) {
            log.info("중복 재고 차감 완료 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handleInventoryDeducted(event);
    }

    @Transactional
    public void processInventoryDeductionFailed(@CorrelationIdSource InventoryDeductionEvent event) {
        String eventKey = event.processedEventId("INVENTORY_DEDUCTION_FAILED");
        if (!idempotencyService.tryRegister("INVENTORY_DEDUCTION_FAILED", eventKey)) {
            log.info("중복 재고 차감 실패 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handleInventoryDeductionFailed(event);
    }

    @Transactional
    public void processInventoryReleased(@CorrelationIdSource InventoryReleaseEvent event) {
        String eventKey = event.processedEventId("INVENTORY_RELEASED");
        if (!idempotencyService.tryRegister("INVENTORY_RELEASED", eventKey)) {
            log.info("중복 재고 예약 해제 완료 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handleInventoryReleased(event);
    }

    @Transactional
    public void processInventoryReleaseFailed(@CorrelationIdSource InventoryReleaseEvent event) {
        String eventKey = event.processedEventId("INVENTORY_RELEASE_FAILED");
        if (!idempotencyService.tryRegister("INVENTORY_RELEASE_FAILED", eventKey)) {
            log.info("중복 재고 예약 해제 실패 이벤트 무시: {}", eventKey);
            return;
        }
        orderService.handleInventoryReleaseFailed(event);
    }
}
