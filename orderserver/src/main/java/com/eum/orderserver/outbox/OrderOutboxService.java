package com.eum.orderserver.outbox;

import com.eum.orderserver.dto.product.CheckoutValidationResponse;
import com.eum.orderserver.message.order.OrderCancelledEvent;
import com.eum.orderserver.message.order.OrderCheckedOutEvent;
import com.eum.orderserver.message.order.OrderCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxService {

    private static final String AGGREGATE_TYPE = "ORDER";

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueueOrderCheckedOut(Long orderId, Long userId, String receiverName,
                                       String receiverPhone, String receiverAddr,
                                       Long amount, Long totalPrice, LocalDateTime capturedAt,
                                       List<CheckoutValidationResponse.Item> items) {
        enqueue(orderId, OutboxEventType.ORDER_CHECKED_OUT,
                OrderCheckedOutEvent.of(orderId, userId, receiverName, receiverPhone, receiverAddr,
                        amount, totalPrice, capturedAt, items));
    }

    public void enqueueOrderCompleted(Long orderId, Long userId, Long amount) {
        enqueue(orderId, OutboxEventType.ORDER_COMPLETED,
                OrderCompletedEvent.of(orderId, userId, amount));
    }

    public void enqueueOrderCancelled(Long orderId, Long userId, String reason) {
        enqueue(orderId, OutboxEventType.ORDER_CANCELLED,
                OrderCancelledEvent.of(orderId, userId, reason));
    }

    private void enqueue(Long orderId, OutboxEventType type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OrderOutbox outbox = new OrderOutbox(AGGREGATE_TYPE, orderId, type.getEventName(), json);
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("아웃박스 payload 직렬화 실패", e);
        }
    }
}