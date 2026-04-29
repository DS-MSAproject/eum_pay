package com.eum.paymentserver.service;

import com.eum.common.correlation.Correlated;
import com.eum.common.correlation.CorrelationIdSource;
import com.eum.paymentserver.client.TossPaymentsClient;
import com.eum.paymentserver.domain.CancelReasonType;
import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentAttempt;
import com.eum.paymentserver.domain.PaymentCancel;
import com.eum.paymentserver.domain.PaymentMethod;
import com.eum.paymentserver.domain.PaymentState;
import com.eum.paymentserver.dto.CancelPaymentRequest;
import com.eum.paymentserver.dto.ConfirmPaymentRequest;
import com.eum.paymentserver.dto.OrderCancelledMessage;
import com.eum.paymentserver.dto.PaymentRequestedMessage;
import com.eum.paymentserver.dto.PaymentResponse;
import com.eum.paymentserver.dto.PreparePaymentRequest;
import com.eum.paymentserver.dto.PreparePaymentResponse;
import com.eum.paymentserver.dto.TossCancelRequest;
import com.eum.paymentserver.dto.TossConfirmRequest;
import com.eum.paymentserver.dto.TossPaymentResponse;
import com.eum.paymentserver.repository.PaymentAttemptRepository;
import com.eum.paymentserver.repository.PaymentCancelRepository;
import com.eum.paymentserver.repository.PaymentRepository;
import com.eum.paymentserver.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentOutboxService paymentOutboxService;
    private final PaymentSseService paymentSseService;
    private final TossPaymentsClient tossPaymentsClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentCancelRepository paymentCancelRepository,
            PaymentOutboxService paymentOutboxService,
            PaymentSseService paymentSseService,
            TossPaymentsClient tossPaymentsClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentCancelRepository = paymentCancelRepository;
        this.paymentOutboxService = paymentOutboxService;
        this.paymentSseService = paymentSseService;
        this.tossPaymentsClient = tossPaymentsClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Correlated
    @Transactional
    public PreparePaymentResponse prepare(@CorrelationIdSource String correlationId, Long userId, PreparePaymentRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .map(existing -> {
                    existing.refreshPrepareContext(request.getAmount(), request.getCurrency());
                    return existing;
                })
                .orElseGet(() -> Payment.ready(
                        request.getOrderId(),
                        userId,
                        request.getAmount(),
                        request.getCurrency()
                ));

        Payment saved = paymentRepository.save(payment);
        return PreparePaymentResponse.builder()
                .paymentId(saved.getPaymentId())
                .orderId(saved.getOrderId())
                .orderName(request.getOrderName())
                .amount(saved.getAmount())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .currency(saved.getCurrency())
                .status(saved.getStatus().name())
                .build();
    }

    @Correlated
    @Transactional
    public void prepareRequestedPayment(@CorrelationIdSource PaymentRequestedMessage message) {
        Payment payment = paymentRepository.findByOrderId(message.getOrderId())
                .map(existing -> {
                    if (existing.getStatus() == PaymentState.APPROVED
                            || existing.getStatus() == PaymentState.CANCELED) {
                        return existing;
                    }
                    existing.refreshPrepareContext(message.getAmount(), "KRW");
                    return existing;
                })
                .orElseGet(() -> Payment.ready(
                        message.getOrderId(),
                        message.getUserId(),
                        message.getAmount(),
                        "KRW"
                ));
        paymentRepository.save(payment);
    }

    /**
     * Toss 결제 승인 흐름을 3단계로 분리해 DB 커넥션을 외부 API 호출 동안 점유하지 않도록 한다.
     * Phase 1 — 검증 (단기 읽기 TX)
     * Phase 2 — Toss 승인 API 호출 (TX 없음)
     * Phase 3 — 결과 기록 + Outbox 적재 (단기 쓰기 TX, 원자적)
     */
    @Correlated
    public PaymentResponse confirm(@CorrelationIdSource String correlationId, Long userId, ConfirmPaymentRequest request) {
        // Phase 1: 입력 검증 및 결제 조회

        log.info("PaymentService.confirm={}", request.getPaymentKey());
        Payment payment = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("결제 준비 정보가 존재하지 않습니다."));
            if (!p.getAmount().equals(request.getAmount())) {
                throw new IllegalArgumentException("결제 승인 금액이 준비 금액과 일치하지 않습니다.");
            }
            if (!p.getUserId().equals(userId)) {
                throw new IllegalArgumentException("해당 결제에 대한 권한이 없습니다.");
            }
            return p;
        }));

        log.info("PaymentService.confirm.getStatus()={}", payment.getStatus());

        if (payment.getStatus() == PaymentState.APPROVED
                && request.getPaymentKey().equals(payment.getPaymentKey())) {
            return toResponse(payment);
        }

        // Phase 2: Toss 승인 API 호출 — DB 커넥션 미점유
        TossConfirmRequest tossRequest = TossConfirmRequest.builder()
                .paymentKey(request.getPaymentKey())
                .orderId("order-" + request.getOrderId())
                .amount(request.getAmount())
                .build();

        try {
            TossPaymentResponse tossResponse = tossPaymentsClient
                    .confirm(payment.getIdempotencyKey(), tossRequest).block();

            log.info("PaymentService.confirm.tossResponse()={}", tossResponse.getPaymentKey());


            // Phase 3: 승인 결과 기록 + Outbox 이벤트 적재 (원자적)
            Payment saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
                Payment p = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow();
                p.approve(
                        tossResponse.getPaymentKey(),
                        PaymentMethod.from(tossResponse.getMethod()),
                        Optional.ofNullable(tossResponse.getEasyPay())
                                .map(TossPaymentResponse.EasyPay::getProvider)
                                .orElse(null)
                );
                paymentAttemptRepository.save(PaymentAttempt.of(
                        p, "CONFIRM", writeJson(tossRequest), writeJson(tossResponse), "SUCCESS"));
                Payment persisted = paymentRepository.save(p);
                paymentOutboxService.enqueueCompleted(persisted, correlationId);
                return persisted;
            }));

            log.info("PaymentService.confirm.");

            paymentSseService.publishCompleted(saved);
            return toResponse(saved);

        } catch (CallNotPermittedException ex) {
            log.warn("Toss 결제 승인 차단됨 (CB OPEN): orderId={}", request.getOrderId());
            throw new ServiceUnavailableException("결제 서비스가 일시적으로 사용 불가합니다. 잠시 후 다시 시도해주세요.");
        } catch (WebClientResponseException ex) {
            log.error("Toss 승인 API 오류: orderId={}, status={}, body={}",
                    request.getOrderId(), ex.getStatusCode(), ex.getResponseBodyAsString());
            // Phase 3: 실패 결과 기록 + Outbox 이벤트 적재 (원자적)
            Payment saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
                Payment p = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow();
                p.fail(String.valueOf(ex.getStatusCode().value()), ex.getResponseBodyAsString());
                paymentAttemptRepository.save(PaymentAttempt.of(
                        p, "CONFIRM", writeJson(tossRequest), ex.getResponseBodyAsString(), "FAILED"));
                Payment persisted = paymentRepository.save(p);
                paymentOutboxService.enqueueFailed(persisted, correlationId);
                return persisted;
            }));
            paymentSseService.publishFailed(saved);
            throw new IllegalStateException("Toss 승인 API 호출에 실패했습니다.");
        } catch (Exception ex) {
            if (Exceptions.unwrap(ex) instanceof TimeoutException) {
                log.warn("Toss 결제 승인 타임아웃: orderId={}", request.getOrderId());
                Payment saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
                    Payment p = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow();
                    p.fail("TIMEOUT", "Toss API 응답 시간 초과");
                    paymentAttemptRepository.save(PaymentAttempt.of(
                            p, "CONFIRM", writeJson(tossRequest), "TIMEOUT", "TIMEOUT"));
                    Payment persisted = paymentRepository.save(p);
                    paymentOutboxService.enqueueFailed(persisted, correlationId);
                    return persisted;
                }));
                paymentSseService.publishFailed(saved);
                throw new IllegalStateException("결제 서비스 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
            }
            log.error("Toss 승인 중 예기치 않은 오류: orderId={}", request.getOrderId(), ex);
            throw new IllegalStateException("결제 처리 중 오류가 발생했습니다.");
        }
    }

    @Correlated
    public PaymentResponse cancel(@CorrelationIdSource String correlationId, Long userId, String paymentId, CancelPaymentRequest request) {
        Payment payment = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findByPaymentId(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
            if (!p.getUserId().equals(userId)) {
                throw new IllegalArgumentException("해당 결제에 대한 권한이 없습니다.");
            }
            return p;
        }));
        return cancelApprovedPayment(correlationId, payment, request);
    }

    @Correlated
    public void compensateOrderCancelled(@CorrelationIdSource OrderCancelledMessage message) {
        Payment payment = transactionTemplate.execute(status ->
                paymentRepository.findByOrderId(message.getOrderId()).orElse(null)
        );

        if (payment == null
                || payment.getStatus() == PaymentState.CANCELED
                || payment.getStatus() != PaymentState.APPROVED) {
            return;
        }

        CancelPaymentRequest request = new CancelPaymentRequest();
        request.setReason(resolveCancelReason(message.getReason()));
        request.setReasonType(CancelReasonType.ORDER_CANCELLATION);
        request.setCancelAmount(payment.getAmount());
        cancelApprovedPayment(message.getCorrelationId(), payment, request);
    }

    // 관리자 강제 취소 — userId 검증 없이 직접 취소 (AdminPaymentService 전용)
    PaymentResponse adminForceCancel(String correlationId, Payment payment, CancelPaymentRequest request) {
        return cancelApprovedPayment(correlationId, payment, request);
    }

    /**
     * Toss 결제 취소 흐름도 confirm과 동일하게 TX를 외부 API 호출 전후로 분리한다.
     */
    private PaymentResponse cancelApprovedPayment(String correlationId, Payment payment, CancelPaymentRequest request) {
        if (payment.getStatus() == PaymentState.CANCELED) {
            return toResponse(payment);
        }
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new IllegalStateException("결제 취소를 위한 paymentKey가 존재하지 않습니다.");
        }

        TossCancelRequest tossRequest = TossCancelRequest.builder()
                .cancelReason(request.getReason())
                .cancelAmount(request.getCancelAmount())
                .build();

        try {
            TossPaymentResponse tossResponse = tossPaymentsClient
                    .cancel(payment.getPaymentKey(), payment.getIdempotencyKey(), tossRequest).block();

            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                Payment p = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
                p.cancel();
                paymentCancelRepository.save(PaymentCancel.success(
                        p, request.getReason(), request.getReasonType(),
                        request.getCancelAmount(), tossResponse.getStatus(), extractCancelId(tossResponse)
                ));
                Payment saved = paymentRepository.save(p);
                paymentOutboxService.enqueueCancelled(saved, request.getReason(), correlationId);
                return toResponse(saved);
            }));

        } catch (CallNotPermittedException ex) {
            log.warn("Toss 결제 취소 차단됨 (CB OPEN): paymentId={}", payment.getPaymentId());
            throw new ServiceUnavailableException("결제 취소 서비스가 일시적으로 사용 불가합니다. 잠시 후 다시 시도해주세요.");
        } catch (WebClientResponseException ex) {
            transactionTemplate.execute(status -> {
                Payment p = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
                p.cancelFailed(String.valueOf(ex.getStatusCode().value()), ex.getResponseBodyAsString());
                paymentCancelRepository.save(PaymentCancel.failure(
                        p, request.getReason(), request.getReasonType(),
                        request.getCancelAmount(),
                        String.valueOf(ex.getStatusCode().value()), ex.getResponseBodyAsString()
                ));
                paymentRepository.save(p);
                return null;
            });
            throw new IllegalStateException("Toss 취소 API 호출에 실패했습니다.");
        } catch (Exception ex) {
            if (Exceptions.unwrap(ex) instanceof TimeoutException) {
                log.warn("Toss 결제 취소 타임아웃: paymentId={}", payment.getPaymentId());
                transactionTemplate.execute(status -> {
                    Payment p = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
                    p.cancelFailed("TIMEOUT", "Toss 취소 API 응답 시간 초과");
                    paymentCancelRepository.save(PaymentCancel.failure(
                            p, request.getReason(), request.getReasonType(),
                            request.getCancelAmount(), "TIMEOUT", "응답 시간 초과"
                    ));
                    paymentRepository.save(p);
                    return null;
                });
                throw new IllegalStateException("결제 취소 서비스 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
            }
            log.error("Toss 취소 중 예기치 않은 오류: paymentId={}", payment.getPaymentId(), ex);
            throw new IllegalStateException("결제 취소 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문에 해당하는 결제 정보를 찾을 수 없습니다."));
        return toResponse(payment);
    }

    private String resolveCancelReason(String reason) {
        return (reason == null || reason.isBlank()) ? "ORDER_CANCELLED" : reason;
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .provider(payment.getProvider().name())
                .method(payment.getMethod().name())
                .easyPayProvider(payment.getEasyPayProvider())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentKey(payment.getPaymentKey())
                .status(payment.getStatus().name())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .approvedAt(payment.getApprovedAt())
                .canceledAt(payment.getCanceledAt())
                .build();
    }

    private String extractCancelId(TossPaymentResponse response) {
        if (response.getCancels() == null || response.getCancels().length == 0) {
            return null;
        }
        return response.getCancels()[0].getTransactionKey();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("결제 요청/응답 직렬화에 실패했습니다.", ex);
        }
    }
}
