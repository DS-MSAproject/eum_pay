package com.eum.orderserver.controller;

import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.dto.admin.AdminInconsistencyResponse;
import com.eum.orderserver.dto.admin.AdminOrderResponse;
import com.eum.orderserver.dto.admin.AdminOutboxPendingResponse;
import com.eum.orderserver.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    // ── 전체 주문 목록 ─────────────────────────────────
    // GET /admin/orders?page=0&size=20&status=PAYMENT_FAILED
    @GetMapping("/admin/orders")
    public ResponseEntity<Page<AdminOrderResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderState status) {

        return ResponseEntity.ok(adminOrderService.listAllOrders(page, size, status));
    }

    // ── 주문-결제 불일치 목록 ──────────────────────────
    // GET /admin/orders/inconsistencies
    @GetMapping("/admin/orders/inconsistencies")
    public ResponseEntity<List<AdminInconsistencyResponse>> getInconsistencies() {
        return ResponseEntity.ok(adminOrderService.getInconsistencies());
    }

    // ── Outbox 미처리 이벤트 목록 ─────────────────────
    // GET /admin/outbox/pending
    @GetMapping("/admin/outbox/pending")
    public ResponseEntity<List<AdminOutboxPendingResponse>> getOutboxPending() {
        return ResponseEntity.ok(adminOrderService.getOutboxPending());
    }

    // ── Outbox 이벤트 재시도 ──────────────────────────
    // POST /admin/outbox/{id}/retry  (id = Orders.id DB PK)
    @PostMapping("/admin/outbox/{id}/retry")
    public ResponseEntity<?> retryEvent(@PathVariable Long id) {
        log.info("[Admin] Outbox 재시도 요청: id={}", id);
        try {
            adminOrderService.retryEvent(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    // ── 보상 트랜잭션 (강제 취소) ─────────────────────
    // POST /admin/outbox/{id}/compensate  (id = Orders.id DB PK)
    @PostMapping("/admin/outbox/{id}/compensate")
    public ResponseEntity<?> applyCompensation(@PathVariable Long id) {
        log.warn("[Admin] 보상 트랜잭션 요청: id={}", id);
        try {
            adminOrderService.applyCompensation(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
