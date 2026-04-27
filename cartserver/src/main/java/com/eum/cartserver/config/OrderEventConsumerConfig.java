package com.eum.cartserver.config;

import com.eum.cartserver.dto.CartItemDeleteRequest;
import com.eum.cartserver.message.OrderCheckedOutMessage;
import com.eum.cartserver.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderEventConsumerConfig {

    private final CartService cartService;

    @Bean
    public Consumer<OrderCheckedOutMessage> orderCheckedOutConsumer() {
        return message -> {
            if (message == null || message.getUserId() == null
                    || message.getItems() == null || message.getItems().isEmpty()) {
                log.warn("유효하지 않은 OrderCheckedOut 이벤트 수신. eventId={}", message != null ? message.getEventId() : null);
                return;
            }

            log.info("OrderCheckedOut 수신 — 장바구니 항목 제거. orderId={}, userId={}, itemCount={}",
                    message.getOrderId(), message.getUserId(), message.getItems().size());

            List<CartItemDeleteRequest> requests = message.getItems().stream()
                    .map(item -> new CartItemDeleteRequest(item.getProductId(), item.getOptionId()))
                    .toList();

            try {
                cartService.removeItems(message.getUserId(), requests);
            } catch (Exception e) {
                log.error("장바구니 항목 제거 실패. orderId={}, userId={}, error={}",
                        message.getOrderId(), message.getUserId(), e.getMessage());
                throw e;
            }
        };
    }
}
