package com.eum.paymentserver.service;

import com.eum.common.correlation.Correlated;
import com.eum.paymentserver.dto.PaymentRequestedMessage;
import com.eum.paymentserver.dto.OrderCancelledMessage;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Correlated
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    public PaymentEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Bean
    public Consumer<PaymentRequestedMessage> paymentRequestedConsumer() {
        return message -> {
            if (message == null || message.getOrderId() == null || message.getUserId() == null || message.getAmount() == null) {
                log.warn("유효하지 않은 PaymentRequested 이벤트를 무시합니다. payload={}", message);
                return;
            }

            log.info("PaymentRequested 수신. eventId={}, orderId={}, userId={}, amount={}",
                    message.processedEventId(), message.getOrderId(), message.getUserId(), message.getAmount());
            paymentService.prepareRequestedPayment(message);
        };
    }

    @Bean
    public Consumer<OrderCancelledMessage> orderCancelledConsumer() {
        return message -> {
            if (message == null || message.getOrderId() == null) {
                log.warn("유효하지 않은 OrderCancelled 이벤트를 무시합니다. payload={}", message);
                return;
            }

            log.info("OrderCancelled 수신. eventId={}, orderId={}, reason={}",
                    message.processedEventId(), message.getOrderId(), message.getReason());
            paymentService.compensateOrderCancelled(message);
        };
    }
}
