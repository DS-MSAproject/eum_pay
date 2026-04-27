package com.eum.paymentserver.service;

import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentOutboxEvent;
import com.eum.paymentserver.repository.PaymentOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOutboxService {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentOutboxService(
            PaymentOutboxEventRepository paymentOutboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.paymentOutboxEventRepository = paymentOutboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueCompleted(Payment payment) {
        save(payment, "PaymentCompleted", basePayload(payment, "PaymentCompleted"));
    }

    @Transactional
    public void enqueueFailed(Payment payment) {
        Map<String, Object> payload = basePayload(payment, "PaymentFailed");
        payload.put("failureCode", payment.getFailureCode());
        payload.put("failureMessage", payment.getFailureMessage());
        payload.put("reason", payment.getFailureMessage());
        save(payment, "PaymentFailed", payload);
    }

    @Transactional
    public void enqueueCancelled(Payment payment, String reason) {
        Map<String, Object> payload = basePayload(payment, "PaymentCancelled");
        payload.put("reason", reason);
        payload.put("cancelReason", reason);
        payload.put("canceledAt", payment.getCanceledAt() != null ? payment.getCanceledAt().toString() : null);
        save(payment, "PaymentCancelled", payload);
    }

    private Map<String, Object> basePayload(Payment payment, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("aggregateType", "PAYMENT");
        payload.put("aggregateId", payment.getOrderId());
        payload.put("orderId", payment.getOrderId());
        payload.put("userId", payment.getUserId());
        payload.put("paymentId", payment.getPaymentId());
        payload.put("occurredAt", LocalDateTime.now().toString());
        payload.put("amount", payment.getAmount());
        payload.put("provider", payment.getProvider().name());
        payload.put("status", payment.getStatus().name());
        payload.put("paymentStatus", resolvePaymentStatus(eventType));
        payload.put("method", payment.getMethod().name());
        return payload;
    }

    private void save(Payment payment, String eventType, Map<String, Object> payload) {
        // aggregate_id = orderId → Debezium이 이 값을 Kafka 메시지 키로 사용
        // order/inventory outbox와 동일하게 orderId 기준 파티셔닝 보장
        paymentOutboxEventRepository.save(
                PaymentOutboxEvent.pending(String.valueOf(payment.getOrderId()), eventType, writeJson(payload))
        );
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("결제 아웃박스 페이로드 직렬화에 실패했습니다.", exception);
        }
    }

    private String resolvePaymentStatus(String eventType) {
        return switch (eventType) {
            case "PaymentCompleted" -> "PAYCOMPLETE";
            case "PaymentFailed" -> "PAYFAIL";
            case "PaymentCancelled" -> "PAYCANCEL";
            default -> eventType;
        };
    }
}
