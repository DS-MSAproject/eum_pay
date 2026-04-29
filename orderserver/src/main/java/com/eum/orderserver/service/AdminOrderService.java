package com.eum.orderserver.service;

import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.domain.Orders;
import com.eum.orderserver.dto.admin.AdminInconsistencyResponse;
import com.eum.orderserver.dto.admin.AdminOrderResponse;
import com.eum.orderserver.dto.admin.AdminOrderStatsResponse;
import com.eum.orderserver.dto.admin.AdminOutboxPendingResponse;
import com.eum.orderserver.dto.admin.ProductSalesResponse;
import com.eum.orderserver.dto.product.CheckoutValidationResponse;
import com.eum.orderserver.outbox.OrderOutboxService;
import com.eum.orderserver.repository.OrderDetailsRepository;
import com.eum.orderserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderService {

    private static final Set<OrderState> STUCK_STATES = Set.of(
            OrderState.ORDER_CHECKED_OUT,
            OrderState.INVENTORY_RESERVED
    );
    private static final Set<OrderState> FAILED_STATES = Set.of(
            OrderState.INVENTORY_RESERVATION_FAILED,
            OrderState.PAYMENT_FAILED,
            OrderState.INVENTORY_DEDUCTION_FAILED,
            OrderState.INVENTORY_RELEASE_FAILED
    );
    private static final int STUCK_THRESHOLD_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final OrderDetailsRepository orderDetailsRepository;
    private final OrderOutboxService outboxService;

    // ── 대시보드 통계 ──────────────────────────────────
    public AdminOrderStatsResponse getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrders = orderRepository.countByTimeGreaterThanEqual(todayStart);
        long totalOrders = orderRepository.count();
        long failedOrders = orderRepository.countByOrderStateIn(FAILED_STATES);

        Map<String, Long> breakdown = new LinkedHashMap<>();
        orderRepository.countGroupByOrderState().forEach(row ->
                breakdown.put(((OrderState) row[0]).getState(), (Long) row[1]));

        return AdminOrderStatsResponse.builder()
                .todayOrders(todayOrders)
                .totalOrders(totalOrders)
                .failedOrders(failedOrders)
                .orderStatusBreakdown(breakdown)
                .build();
    }

    // ── 제품별 판매량 집계 ─────────────────────────────────
    public List<ProductSalesResponse> getProductSales(int limit) {
        return orderDetailsRepository.findTopProductSales(Math.min(limit, 50))
                .stream()
                .map(row -> ProductSalesResponse.builder()
                        .productId(((Number) row[0]).longValue())
                        .productName((String) row[1])
                        .totalQuantity(((Number) row[2]).longValue())
                        .totalRevenue(((Number) row[3]).longValue())
                        .build())
                .toList();
    }

    // ── 전체 주문 목록 (관리자) ──────────────────────────
    public Page<AdminOrderResponse> listAllOrders(int page, int size, OrderState status) {
        PageRequest pageable = PageRequest.of(page, Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "time"));

        Specification<Orders> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("orderState"), status));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return orderRepository.findAll(spec, pageable).map(AdminOrderResponse::from);
    }

    // ── 주문-결제 불일치 목록 ──────────────────────────────
    public List<AdminInconsistencyResponse> getInconsistencies() {
        List<AdminInconsistencyResponse> result = new ArrayList<>();

        // 실패 상태 주문들 — 각 상태별 원인 메시지
        for (OrderState state : FAILED_STATES) {
            Specification<Orders> spec = (root, query, cb) ->
                    cb.equal(root.get("orderState"), state);
            orderRepository.findAll(spec).forEach(o ->
                    result.add(AdminInconsistencyResponse.of(o, describeInconsistency(o.getOrderState(), o.getFailedReason())))
            );
        }

        // 중간 상태에 오래 머물고 있는 주문 (10분 이상)
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        for (OrderState state : STUCK_STATES) {
            Specification<Orders> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("orderState"), state),
                    cb.lessThan(root.get("time"), threshold)
            );
            orderRepository.findAll(spec).forEach(o ->
                    result.add(AdminInconsistencyResponse.of(o,
                            state.getState() + " 상태로 " + STUCK_THRESHOLD_MINUTES + "분 이상 대기 중 (이벤트 미수신 의심)"))
            );
        }

        return result;
    }

    // ── Outbox 미처리 이벤트 (실패 + stuck 주문) ─────────
    public List<AdminOutboxPendingResponse> getOutboxPending() {
        List<Orders> candidates = new ArrayList<>();

        // 실패 상태 전체
        Specification<Orders> failedSpec = (root, query, cb) ->
                root.get("orderState").in(FAILED_STATES);
        candidates.addAll(orderRepository.findAll(failedSpec));

        // 중간 상태에 오래 머무른 주문
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        Specification<Orders> stuckSpec = (root, query, cb) -> cb.and(
                root.get("orderState").in(STUCK_STATES),
                cb.lessThan(root.get("time"), threshold)
        );
        candidates.addAll(orderRepository.findAll(stuckSpec));

        return candidates.stream()
                .sorted((a, b) -> {
                    LocalDateTime ta = a.getFailedAt() != null ? a.getFailedAt() : a.getTime();
                    LocalDateTime tb = b.getFailedAt() != null ? b.getFailedAt() : b.getTime();
                    return tb.compareTo(ta);
                })
                .map(AdminOutboxPendingResponse::from)
                .toList();
    }

    // ── Outbox 이벤트 재시도 ────────────────────────────
    // id = Orders.id (DB PK)
    @Transactional
    public void retryEvent(Long id) {
        Orders order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + id));

        OrderState state = order.getOrderState();
        log.info("[Admin] 이벤트 재시도 요청: orderId={}, state={}", order.getOrderId(), state);

        switch (state) {
            case ORDER_CHECKED_OUT, INVENTORY_RESERVATION_FAILED -> {
                // ORDER_CHECKED_OUT 이벤트 재발행 → inventoryserver가 재처리
                var details = orderDetailsRepository.findAllByOrdersOrderIdOrderByIdAsc(order.getOrderId());
                if (details.isEmpty()) {
                    throw new IllegalStateException("재시도에 필요한 주문 상세 정보가 없습니다.");
                }
                var items = details.stream().map(this::toCheckoutItem).toList();
                // INVENTORY_RESERVATION_FAILED 상태면 ORDER_CHECKED_OUT으로 초기화 후 재발행
                if (state == OrderState.INVENTORY_RESERVATION_FAILED) {
                    order.setOrderState(OrderState.ORDER_CHECKED_OUT);
                    order.setFailedReason(null);
                    order.setFailedAt(null);
                }
                outboxService.enqueueOrderCheckedOut(
                        order.getOrderId(), order.getUserId(),
                        order.getReceiverName(), order.getReceiverPhone(), order.getReceiverAddr(),
                        order.getAmount(), order.getAmount(),
                        order.getTime(), items
                );
                log.info("[Admin] ORDER_CHECKED_OUT 재발행 완료: orderId={}", order.getOrderId());
            }
            case PAYMENT_FAILED, INVENTORY_RELEASE_FAILED -> {
                // ORDER_CANCELLED 이벤트 재발행 → inventoryserver가 재고 해제 재시도
                order.setOrderState(OrderState.ORDER_CANCELLED);
                outboxService.enqueueOrderCancelled(order.getOrderId(), order.getUserId(),
                        "[관리자 재시도] 결제 실패 보상");
                log.info("[Admin] ORDER_CANCELLED 재발행 완료: orderId={}", order.getOrderId());
            }
            default -> throw new IllegalStateException(
                    "현재 상태(" + state.getState() + ")에서는 재시도를 지원하지 않습니다. 보상 트랜잭션을 사용하세요.");
        }
    }

    // ── 보상 트랜잭션 (강제 취소) ──────────────────────────
    // id = Orders.id (DB PK)
    @Transactional
    public void applyCompensation(Long id) {
        Orders order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + id));

        OrderState current = order.getOrderState();
        if (current == OrderState.ORDER_COMPLETED || current == OrderState.ORDER_CANCELLED) {
            throw new IllegalStateException("이미 완료되었거나 취소된 주문입니다: " + current.getState());
        }

        log.warn("[Admin] 보상 트랜잭션 실행: orderId={}, state={}", order.getOrderId(), current);
        order.setOrderState(OrderState.ORDER_CANCELLED);
        order.setFailedReason("[관리자 강제취소] 이전 상태: " + current.getState());
        order.setFailedAt(LocalDateTime.now());

        outboxService.enqueueOrderCancelled(order.getOrderId(), order.getUserId(),
                "[관리자 보상] " + current.getState() + " 상태에서 강제 취소");
        log.warn("[Admin] 강제 취소 완료 및 ORDER_CANCELLED 발행: orderId={}", order.getOrderId());
    }

    // ── 헬퍼 ──────────────────────────────────────────────
    private CheckoutValidationResponse.Item toCheckoutItem(
            com.eum.orderserver.domain.OrderDetails detail) {
        Map<String, Object> snap = detail.getSnapshotJson();
        var item = new CheckoutValidationResponse.Item();
        item.setProductId(detail.getProductId());
        item.setOptionId(detail.getOptionId());
        item.setProductName(detail.getProductName());
        item.setOptionName(detail.getOptionName());
        item.setPrice(detail.getPrice());
        item.setQuantity(detail.getQuantity());
        item.setLineTotalPrice(detail.getTotalPrice());
        if (snap != null) {
            item.setDiscountPrice(toLong(snap.get("discountPrice")));
            item.setExtraPrice(toLong(snap.get("extraPrice")));
        }
        return item;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String describeInconsistency(OrderState state, String reason) {
        return switch (state) {
            case INVENTORY_RESERVATION_FAILED -> "재고 예약 실패" + (reason != null ? ": " + reason : "");
            case PAYMENT_FAILED               -> "결제 실패" + (reason != null ? ": " + reason : "");
            case INVENTORY_DEDUCTION_FAILED   -> "재고 차감 실패 (결제 완료 후 불일치)" + (reason != null ? ": " + reason : "");
            case INVENTORY_RELEASE_FAILED     -> "재고 해제 실패 (취소/실패 후 불일치)" + (reason != null ? ": " + reason : "");
            default -> state.getState();
        };
    }
}
