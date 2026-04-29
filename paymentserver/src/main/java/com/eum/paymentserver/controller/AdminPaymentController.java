package com.eum.paymentserver.controller;

import com.eum.paymentserver.domain.PaymentState;
import com.eum.paymentserver.dto.admin.AdminIdempotencyViolationResponse;
import com.eum.paymentserver.dto.admin.AdminPaymentResponse;
import com.eum.paymentserver.dto.admin.AdminPaymentStatsResponse;
import com.eum.paymentserver.dto.admin.AdminReconciliationResponse;
import com.eum.paymentserver.service.AdminPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    // ── 대시보드 통계 ──────────────────────────────────
    // GET /admin/payments/stats
    @GetMapping("/admin/payments/stats")
    public ResponseEntity<AdminPaymentStatsResponse> getStats() {
        return ResponseEntity.ok(adminPaymentService.getStats());
    }

    // ── 결제 목록 ──────────────────────────────────────
    // GET /admin/payments?page=0&size=20&status=FAILED
    @GetMapping("/admin/payments")
    public ResponseEntity<Page<AdminPaymentResponse>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PaymentState status) {

        return ResponseEntity.ok(adminPaymentService.listAllPayments(page, size, status));
    }

    // ── 정산 리포트 ────────────────────────────────────
    // GET /admin/payments/reconciliation
    @GetMapping("/admin/payments/reconciliation")
    public ResponseEntity<AdminReconciliationResponse> getReconciliation() {
        return ResponseEntity.ok(adminPaymentService.getReconciliationReport());
    }

    // ── 멱등성 위반 목록 ──────────────────────────────
    // GET /admin/payments/idempotency-violations
    @GetMapping("/admin/payments/idempotency-violations")
    public ResponseEntity<List<AdminIdempotencyViolationResponse>> getIdempotencyViolations() {
        return ResponseEntity.ok(adminPaymentService.getIdempotencyViolations());
    }

    // ── 결제 Outbox 재시도 ────────────────────────────
    // POST /admin/payments/{paymentId}/retry
    @PostMapping("/admin/payments/{paymentId}/retry")
    public ResponseEntity<?> retryPayment(@PathVariable String paymentId) {
        log.info("[Admin] 결제 재시도 요청: paymentId={}", paymentId);
        try {
            adminPaymentService.retryPayment(paymentId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    // ── 강제 환불 ──────────────────────────────────────
    // POST /admin/payments/{paymentId}/force-refund
    @PostMapping("/admin/payments/{paymentId}/force-refund")
    public ResponseEntity<?> forceRefund(@PathVariable String paymentId) {
        log.warn("[Admin] 강제 환불 요청: paymentId={}", paymentId);
        try {
            adminPaymentService.forceRefund(paymentId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
