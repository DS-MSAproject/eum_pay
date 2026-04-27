package com.eum.paymentserver.controller;

import com.eum.common.correlation.CorrelationConstants;
import com.eum.paymentserver.dto.CancelPaymentRequest;
import com.eum.paymentserver.dto.ConfirmPaymentRequest;
import com.eum.paymentserver.dto.PaymentResponse;
import com.eum.paymentserver.dto.PaymentStatusSseEvent;
import com.eum.paymentserver.dto.PreparePaymentRequest;
import com.eum.paymentserver.dto.PreparePaymentResponse;
import com.eum.paymentserver.service.PaymentSseService;
import com.eum.paymentserver.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentSseService paymentSseService;

    public PaymentController(PaymentService paymentService, PaymentSseService paymentSseService) {
        this.paymentService = paymentService;
        this.paymentSseService = paymentSseService;
    }

    @PostMapping("/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PreparePaymentResponse> prepare(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = CorrelationConstants.CORRELATION_HEADER, required = false) String correlationId,
            @Valid @RequestBody PreparePaymentRequest request
    ) {
        return Mono.fromCallable(() -> paymentService.prepare(correlationId, userId, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/confirm")
    public Mono<PaymentResponse> confirm(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = CorrelationConstants.CORRELATION_HEADER, required = false) String correlationId,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return Mono.fromCallable(() -> paymentService.confirm(correlationId, userId, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{paymentId}/cancel")
    public Mono<PaymentResponse> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = CorrelationConstants.CORRELATION_HEADER, required = false) String correlationId,
            @PathVariable String paymentId,
            @Valid @RequestBody CancelPaymentRequest request
    ) {
        return Mono.fromCallable(() -> paymentService.cancel(correlationId, userId, paymentId, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/orders/{orderId}")
    public Mono<PaymentResponse> getByOrderId(@PathVariable Long orderId) {
        return Mono.fromCallable(() -> paymentService.getByOrderId(orderId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(value = "/orders/{orderId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<PaymentStatusSseEvent>> subscribePaymentEvents(@PathVariable Long orderId) {
        return paymentSseService.subscribe(orderId);
    }
}
