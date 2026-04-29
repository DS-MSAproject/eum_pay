package com.eum.inventoryserver.controller;

import com.eum.inventoryserver.dto.admin.AdminInventoryEventResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryLagAlertResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryStatsResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryTraceResponse;
import com.eum.inventoryserver.service.AdminInventoryService;
import com.eum.inventoryserver.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public class InventoryAdminController {

    private final InventoryService inventoryService;
    private final AdminInventoryService adminInventoryService;

    // ── 대시보드 통계 ──────────────────────────────────
    // GET /admin/inventory/stats
    @GetMapping("/stats")
    public ResponseEntity<AdminInventoryStatsResponse> getStats() {
        return ResponseEntity.ok(adminInventoryService.getStats());
    }

    // ── 재고 현황 목록 ─────────────────────────────────
    // GET /admin/inventory
    @GetMapping
    public ResponseEntity<List<AdminInventoryResponse>> listInventory() {
        return ResponseEntity.ok(adminInventoryService.listAllInventory());
    }

    // ── 예약 이벤트 히스토리 ───────────────────────────
    // GET /admin/inventory/events?page=0&size=20
    @GetMapping("/events")
    public ResponseEntity<Page<AdminInventoryEventResponse>> listEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminInventoryService.listEventHistory(page, size));
    }

    // ── RESERVED 지연 알림 ─────────────────────────────
    // GET /admin/inventory/lag-alerts
    @GetMapping("/lag-alerts")
    public ResponseEntity<List<AdminInventoryLagAlertResponse>> getLagAlerts() {
        return ResponseEntity.ok(adminInventoryService.getLagAlerts());
    }

    // ── 상품별 재고·예약 이력 추적 ─────────────────────
    // GET /admin/inventory/products/{productId}/trace
    @GetMapping("/products/{productId}/trace")
    public ResponseEntity<AdminInventoryTraceResponse> traceProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(adminInventoryService.traceByProduct(productId));
    }

    // ── 재고 직접 수정 ─────────────────────────────────
    // PATCH /admin/inventory/products/{productId}/options/{optionId}?quantity={n}
    @PatchMapping("/products/{productId}/options/{optionId}")
    public void updateOptionStock(
            @PathVariable Long productId,
            @PathVariable Long optionId,
            @RequestParam int quantity
    ) {
        inventoryService.replaceStock(productId, optionId, quantity);
    }
}
