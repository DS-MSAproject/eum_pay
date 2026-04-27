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

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final Map<Long, Sinks.Many<PaymentStatusSseEvent>> orderChannels = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<PaymentStatusSseEvent>> subscribe(Long orderId) {
        Sinks.Many<PaymentStatusSseEvent> sink = getOrCreateSink(orderId);

        Flux<ServerSentEvent<PaymentStatusSseEvent>> updates = sink.asFlux()
                .map(event -> ServerSentEvent.<PaymentStatusSseEvent>builder()
                        .event("payment-status")
                        .id(String.valueOf(orderId))
                        .data(event)
                        .build());

        // sink가 complete되면 heartbeat도 함께 종료
        Flux<ServerSentEvent<PaymentStatusSseEvent>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(sequence -> ServerSentEvent.<PaymentStatusSseEvent>builder()
                        .comment("keepalive")
                        .build())
                .takeUntilOther(sink.asFlux().then());

        return Flux.merge(updates, heartbeat);
    }

    public void publishCompleted(Payment payment) {
        emitFinal(payment.getOrderId(), PaymentStatusSseEvent.builder()
                .orderId(payment.getOrderId())
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .message("결제가 완료되었습니다.")
                .approvedAt(payment.getApprovedAt())
                .build());
    }

    public void publishFailed(Payment payment) {
        emitFinal(payment.getOrderId(), PaymentStatusSseEvent.builder()
                .orderId(payment.getOrderId())
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .message("결제에 실패했습니다.")
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .failedAt(payment.getFailedAt())
                .build());
    }

    // 최종 이벤트 전송 후 스트림 종료 및 메모리 해제
    private void emitFinal(Long orderId, PaymentStatusSseEvent event) {
        Sinks.Many<PaymentStatusSseEvent> sink = getOrCreateSink(orderId);
        sink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
        sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        orderChannels.remove(orderId);
    }

    private Sinks.Many<PaymentStatusSseEvent> getOrCreateSink(Long orderId) {
        return orderChannels.computeIfAbsent(orderId, ignored -> Sinks.many().replay().limit(1));
    }
}
