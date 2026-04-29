package com.eum.authserver.controller;

import com.eum.authserver.dto.admin.*;
import com.eum.authserver.repository.AdminAuditLogRepository;
import com.eum.authserver.service.AdminDashboardService;
import com.eum.authserver.service.AdminMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminMonitoringController {

    private final AdminMonitoringService adminMonitoringService;
    private final AdminDashboardService adminDashboardService;
    private final AdminAuditLogRepository adminAuditLogRepository;

    // ── 서비스 헬스 현황 ────────────────────────────────
    // GET /admin/monitoring/health
    @GetMapping("/admin/monitoring/health")
    public ResponseEntity<List<ServiceHealthResponse>> getServicesHealth() {
        return ResponseEntity.ok(adminMonitoringService.getServicesHealth());
    }

    // ── Kafka 컨슈머 랙 ─────────────────────────────────
    // GET /admin/monitoring/kafka/consumer-lag
    @GetMapping("/admin/monitoring/kafka/consumer-lag")
    public ResponseEntity<List<KafkaLagResponse>> getKafkaConsumerLag() {
        return ResponseEntity.ok(adminMonitoringService.getKafkaConsumerLag());
    }

    // ── ELK 로그 검색 ───────────────────────────────────
    // GET /admin/monitoring/logs/search
    @GetMapping("/admin/monitoring/logs/search")
    public ResponseEntity<LogSearchResponse> searchLogs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                adminMonitoringService.searchLogs(service, level, keyword, traceId, from, to, size));
    }

    // ── 주문 로그 타임라인 ──────────────────────────────
    // GET /admin/monitoring/logs/order/{orderId}/timeline
    @GetMapping("/admin/monitoring/logs/order/{orderId}/timeline")
    public ResponseEntity<LogSearchResponse> getOrderLogTimeline(@PathVariable String orderId) {
        log.info("[Admin] 주문 로그 타임라인 조회: orderId={}", orderId);
        return ResponseEntity.ok(adminMonitoringService.getOrderLogTimeline(orderId));
    }

    // ── 감사 로그 ───────────────────────────────────────
    // GET /admin/audit-logs?page=0&size=30
    @GetMapping("/admin/audit-logs")
    public ResponseEntity<Page<AdminAuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Page<AdminAuditLogResponse> result = adminAuditLogRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.max(1, size)))
                .map(AdminAuditLogResponse::from);
        return ResponseEntity.ok(result);
    }

    // ── 대시보드 요약 통계 ──────────────────────────────
    // GET /admin/dashboard/summary
    @GetMapping("/admin/dashboard/summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        return ResponseEntity.ok(adminDashboardService.getSummary());
    }
}
