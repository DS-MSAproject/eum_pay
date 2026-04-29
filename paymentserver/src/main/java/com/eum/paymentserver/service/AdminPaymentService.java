package com.eum.paymentserver.service;

import com.eum.paymentserver.domain.CancelReasonType;
import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentAttempt;
import com.eum.paymentserver.domain.PaymentState;
import com.eum.paymentserver.dto.CancelPaymentRequest;
import com.eum.paymentserver.dto.admin.AdminIdempotencyViolationResponse;
import com.eum.paymentserver.dto.admin.AdminPaymentResponse;
import com.eum.paymentserver.dto.admin.AdminReconciliationResponse;
import com.eum.paymentserver.repository.PaymentAttemptRepository;
import com.eum.paymentserver.repository.PaymentCancelRepository;
import com.eum.paymentserver.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentOutboxService paymentOutboxService;
    private final PaymentService paymentService;

    // ── 전체 결제 목록 ────────────────────────────────
    public Page<AdminPaymentResponse> listAllPayments(int page, int size, PaymentState status) {
        PageRequest pageable = PageRequest.of(page, Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return paymentRepository.findAll(spec, pageable).map(AdminPaymentResponse::from);
    }

    // ── 정산 리포트 ────────────────────────────────────
    public AdminReconciliationResponse getReconciliationReport() {
        long totalPaid     = paymentRepository.sumAmountByStatus(PaymentState.APPROVED);
        long totalRefunded = paymentCancelRepository.sumCanceledAmount();
        long discrepancies = paymentRepository.countByStatus(PaymentState.FAILED)
                           + paymentRepository.countByStatus(PaymentState.CANCEL_FAILED);

        return AdminReconciliationResponse.builder()
                .totalPaid(totalPaid)
                .totalRefunded(totalRefunded)
                .netRevenue(totalPaid - totalRefunded)
                .discrepancyCount(discrepancies)
                .build();
    }

    // ── 멱등성 위반 목록 ──────────────────────────────
    public List<AdminIdempotencyViolationResponse> getIdempotencyViolations() {
        List<Payment> violatingPayments = paymentAttemptRepository.findPaymentsWithMultipleConfirmAttempts();
        return violatingPayments.stream().map(p -> {
            List<PaymentAttempt> attempts = paymentAttemptRepository.findByPaymentOrderByCreatedAtAsc(p);
            return AdminIdempotencyViolationResponse.of(p, attempts);
        }).toList();
    }

    // ── Outbox 이벤트 재시도 ──────────────────────────
    // paymentId = Payment.paymentId (비즈니스 ID)
    @Transactional
    public void retryPayment(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다. paymentId=" + paymentId));

        log.info("[Admin] 결제 Outbox 재시도: paymentId={}, status={}", paymentId, payment.getStatus());

        String correlationId = "[admin-retry]";
        switch (payment.getStatus()) {
            case FAILED -> paymentOutboxService.enqueueFailed(payment, correlationId);
            case CANCEL_FAILED -> paymentOutboxService.enqueueCancelled(payment, "[관리자 재시도] 취소 재발행", correlationId);
            default -> throw new IllegalStateException(
                    "재시도 대상 상태가 아닙니다. 현재 상태: " + payment.getStatus());
        }
        log.info("[Admin] 결제 Outbox 재발행 완료: paymentId={}", paymentId);
    }

    // ── 강제 환불 ─────────────────────────────────────
    // paymentId = Payment.paymentId (비즈니스 ID)
    @Transactional
    public void forceRefund(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다. paymentId=" + paymentId));

        if (payment.getStatus() != PaymentState.APPROVED) {
            throw new IllegalStateException("APPROVED 상태의 결제만 강제 환불할 수 있습니다. 현재: " + payment.getStatus());
        }

        log.warn("[Admin] 강제 환불 요청: paymentId={}, orderId={}, amount={}",
                paymentId, payment.getOrderId(), payment.getAmount());

        CancelPaymentRequest req = new CancelPaymentRequest();
        req.setReason("[관리자 강제환불]");
        req.setReasonType(CancelReasonType.ADMIN_FORCE_CANCEL);
        req.setCancelAmount(payment.getAmount());

        // PaymentService.cancel()의 userId 검증을 우회하기 위해 내부 cancelApprovedPayment 호출
        paymentService.adminForceCancel("[admin-force-refund]", payment, req);
        log.warn("[Admin] 강제 환불 완료: paymentId={}", paymentId);
    }
}
