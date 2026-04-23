package com.eum.orderserver.config;

import com.eum.orderserver.message.inventory.InventoryDeductionEvent;
import com.eum.orderserver.message.inventory.InventoryReleaseEvent;
import com.eum.orderserver.message.inventory.InventoryReservationEvent;
import com.eum.orderserver.message.payment.PaymentOrderEvent;
import com.eum.orderserver.service.OrderEventProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class OrderEventConsumerConfig {

    private final OrderEventProcessor processor;

    @Bean
    public Consumer<InventoryReservationEvent> inventoryReservedConsumer() {
        return processor::processInventoryReserved;
    }

    @Bean
    public Consumer<InventoryReservationEvent> inventoryReservationFailedConsumer() {
        return processor::processInventoryReservationFailed;
    }

    @Bean
    public Consumer<PaymentOrderEvent> paymentCompletedConsumer() {
        return processor::processPaymentCompleted;
    }

    @Bean
    public Consumer<PaymentOrderEvent> paymentFailedConsumer() {
        return processor::processPaymentFailed;
    }

    @Bean
    public Consumer<InventoryDeductionEvent> inventoryDeductedConsumer() {
        return processor::processInventoryDeducted;
    }

    @Bean
    public Consumer<InventoryDeductionEvent> inventoryDeductionFailedConsumer() {
        return processor::processInventoryDeductionFailed;
    }

    @Bean
    public Consumer<InventoryReleaseEvent> inventoryReleasedConsumer() {
        return processor::processInventoryReleased;
    }

    @Bean
    public Consumer<InventoryReleaseEvent> inventoryReleaseFailedConsumer() {
        return processor::processInventoryReleaseFailed;
    }
}
