package com.eum.inventoryserver.config;

import com.eum.inventoryserver.message.order.OrderCheckedOutEvent;
import com.eum.inventoryserver.message.payment.PaymentCancelStatusEvent;
import com.eum.inventoryserver.message.payment.PaymentStatusEvent;
import com.eum.inventoryserver.message.product.ProductCreatedEvent;
import com.eum.inventoryserver.service.InventoryOrderEventHandler;
import com.eum.inventoryserver.service.InventoryProductEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class InventoryEventConsumerConfig {

    private final InventoryOrderEventHandler handler;
    private final InventoryProductEventHandler productEventHandler;

    @Bean
    public Consumer<OrderCheckedOutEvent> orderCheckedOutConsumer() {
        return handler::handleOrderCheckedOut;
    }

    @Bean
    public Consumer<PaymentStatusEvent> paymentCompletedConsumer() {
        return handler::handlePaymentCompletedTopic;
    }

    @Bean
    public Consumer<PaymentStatusEvent> paymentFailedConsumer() {
        return handler::handlePaymentFailedTopic;
    }

    @Bean
    public Consumer<PaymentCancelStatusEvent> paymentCancelStatusConsumer() {
        return handler::handlePaymentCancelStatus;
    }

    @Bean
    public Consumer<ProductCreatedEvent> productCreatedConsumer() {
        return productEventHandler::handleProductCreated;
    }
}
