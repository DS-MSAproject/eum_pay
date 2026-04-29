package com.eum.cartserver.config;

import com.eum.cartserver.message.OrderCancelledMessage;
import com.eum.cartserver.message.OrderCheckedOutMessage;
import com.eum.cartserver.message.PaymentCompletedMessage;
import com.eum.cartserver.message.PaymentFailedMessage;
import com.eum.cartserver.service.CartCheckoutSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderEventConsumerConfig {

    private final CartCheckoutSnapshotService snapshotService;

    @Bean
    public Consumer<OrderCheckedOutMessage> orderCheckedOutConsumer() {
        return message -> {
            if (message == null || message.getUserId() == null
                    || message.getItems() == null || message.getItems().isEmpty()) {
                log.warn("유효하지 않은 OrderCheckedOut 이벤트. eventId={}", message != null ? message.getEventId() : null);
                return;
            }
            log.info("OrderCheckedOut 수신 — 스냅샷 저장. orderId={}, userId={}", message.getOrderId(), message.getUserId());
            snapshotService.saveSnapshot(message.getOrderId(), message.getUserId(), message.getItems());
        };
    }

    @Bean
    public Consumer<PaymentCompletedMessage> paymentCompletedConsumer() {
        return message -> {
            if (message == null || message.getOrderId() == null) {
                log.warn("[PaymentCompleted] 유효하지 않은 이벤트 수신. eventId={}", message != null ? message.getEventId() : null);
                return;
            }
            log.info("[PaymentCompleted] 이벤트 수신. orderId={}, userId={}", message.getOrderId(), message.getUserId());
            snapshotService.clearCartOnPaymentCompleted(message.getOrderId());
            log.info("[PaymentCompleted] 처리 완료. orderId={}", message.getOrderId());
        };
    }

    @Bean
    public Consumer<PaymentFailedMessage> paymentFailedConsumer() {
        return message -> {
            if (message == null || message.getOrderId() == null) {
                log.warn("유효하지 않은 PaymentFailed 이벤트. eventId={}", message != null ? message.getEventId() : null);
                return;
            }
            log.info("PaymentFailed 수신 — 스냅샷 삭제 (장바구니 유지). orderId={}", message.getOrderId());
            snapshotService.clearSnapshotOnly(message.getOrderId(), "PaymentFailed");
        };
    }

    @Bean
    public Consumer<OrderCancelledMessage> orderCancelledConsumer() {
        return message -> {
            if (message == null || message.getOrderId() == null) {
                log.warn("유효하지 않은 OrderCancelled 이벤트. eventId={}", message != null ? message.getEventId() : null);
                return;
            }
            log.info("OrderCancelled 수신. orderId={}, userId={}", message.getOrderId(), message.getUserId());
            snapshotService.handleOrderCancelled(message.getOrderId(), message.getUserId());
        };
    }
}
