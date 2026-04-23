package com.eum.paymentserver.service;

import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.dto.PaymentStatusSseEvent;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class PaymentSseService {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(100);

    private final Map<Long, Sinks.Many<PaymentStatusSseEvent>> orderChannels = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<PaymentStatusSseEvent>> subscribe(Long orderId) {
        Sinks.Many<PaymentStatusSseEvent> sink = getOrCreateSink(orderId);

        Flux<ServerSentEvent<PaymentStatusSseEvent>> updates = sink.asFlux()
                .map(event -> ServerSentEvent.<PaymentStatusSseEvent>builder()
                        .event("payment-status")
                        .id(String.valueOf(orderId))
                        .data(event)
                        .build());

        Flux<ServerSentEvent<PaymentStatusSseEvent>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(sequence -> ServerSentEvent.<PaymentStatusSseEvent>builder()
                        .comment("keepalive")
                        .build());

        return Flux.merge(updates, heartbeat);
    }

    public void publishCompleted(Payment payment) {
        emit(payment.getOrderId(), PaymentStatusSseEvent.builder()
                .orderId(payment.getOrderId())
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .message("결제가 완료되었습니다.")
                .approvedAt(payment.getApprovedAt())
                .build());
    }

    public void publishFailed(Payment payment) {
        emit(payment.getOrderId(), PaymentStatusSseEvent.builder()
                .orderId(payment.getOrderId())
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .message("결제에 실패했습니다.")
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .failedAt(payment.getFailedAt())
                .build());
    }

    private void emit(Long orderId, PaymentStatusSseEvent event) {
        Sinks.Many<PaymentStatusSseEvent> sink = getOrCreateSink(orderId);
        sink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    private Sinks.Many<PaymentStatusSseEvent> getOrCreateSink(Long orderId) {
        return orderChannels.computeIfAbsent(orderId, ignored -> Sinks.many().replay().limit(1));
    }
}
