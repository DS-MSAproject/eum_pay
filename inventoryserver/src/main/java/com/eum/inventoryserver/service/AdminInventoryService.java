package com.eum.inventoryserver.service;

import com.eum.inventoryserver.dto.admin.AdminInventoryEventResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryLagAlertResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryStatsResponse;
import com.eum.inventoryserver.dto.admin.AdminInventoryTraceResponse;
import com.eum.inventoryserver.entity.Inventory;
import com.eum.inventoryserver.entity.InventoryReservation;
import com.eum.inventoryserver.entity.InventoryReservationStatus;
import com.eum.inventoryserver.repository.InventoryRepository;
import com.eum.inventoryserver.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInventoryService {

    private static final int LAG_THRESHOLD_MINUTES = 10;

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    // ── 대시보드 통계 ──────────────────────────────────
    public AdminInventoryStatsResponse getStats() {
        long lowStockCount = inventoryRepository.countByStockQuantityLessThanEqual(10);
        return AdminInventoryStatsResponse.builder()
                .lowStockCount(lowStockCount)
                .build();
    }

    // ── 전체 재고 현황 ─────────────────────────────────
    public List<AdminInventoryResponse> listAllInventory() {
        return inventoryRepository.findAll(Sort.by("productId", "optionId"))
                .stream()
                .map(AdminInventoryResponse::from)
                .toList();
    }

    // ── 예약 이벤트 히스토리 (페이지) ──────────────────
    public Page<AdminInventoryEventResponse> listEventHistory(int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return inventoryReservationRepository.findAll(pageable)
                .map(AdminInventoryEventResponse::from);
    }

    // ── RESERVED 상태에서 지연 중인 예약 목록 ──────────
    public List<AdminInventoryLagAlertResponse> getLagAlerts() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(LAG_THRESHOLD_MINUTES);
        return inventoryReservationRepository
                .findAllByStatusAndCreatedAtBefore(InventoryReservationStatus.RESERVED, threshold)
                .stream()
                .map(AdminInventoryLagAlertResponse::from)
                .toList();
    }

    // ── 특정 상품의 재고 + 예약 이력 추적 ──────────────
    public AdminInventoryTraceResponse traceByProduct(Long productId) {
        List<Inventory> stocks = inventoryRepository.findAllByProductId(productId);
        List<InventoryReservation> reservations =
                inventoryReservationRepository.findAllByProductId(productId);
        return AdminInventoryTraceResponse.of(productId, stocks, reservations);
    }
}
