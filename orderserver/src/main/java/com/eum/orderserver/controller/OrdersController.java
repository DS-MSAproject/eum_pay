package com.eum.orderserver.controller;

import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.domain.Orders;
import com.eum.orderserver.dto.OrderDetailResponse;
import com.eum.orderserver.dto.OrderRequest;
import com.eum.orderserver.dto.OrderSummaryResponse;
import com.eum.orderserver.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrdersController {

    private final OrderService orderService;

    @PostMapping("/subject")
    public ResponseEntity<?> order(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody OrderRequest orderRequest) {
        log.info("주문 생성 요청 수신: 유저 ID {}", userId);

        try {
            Orders savedOrder = orderService.register(orderRequest, userId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(savedOrder.getOrderId());

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("주문 생성 요청 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("주문 생성 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("주문 처리 실패");
        }
    }

    @GetMapping
    public ResponseEntity<?> listOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "start_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "end_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "status", required = false) OrderState status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        if (period != null) {
            startDate = parsePeriodToStartDate(period);
            endDate = LocalDate.now();
        }
        int zeroBasedPage = Math.max(0, page - 1);
        log.info("주문 목록 조회: userId={}, period={}, startDate={}, endDate={}, status={}, page={}, size={}",
                userId, period, startDate, endDate, status, page, size);

        try {
            Page<OrderSummaryResponse> response = orderService.listOrders(userId, startDate, endDate, status, zeroBasedPage, size);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("주문 목록 조회 파라미터 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("주문 목록 조회 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("주문 목록 조회 실패");
        }
    }

    private LocalDate parsePeriodToStartDate(String period) {
        try {
            if (period.endsWith("m")) {
                int months = Integer.parseInt(period.substring(0, period.length() - 1));
                return LocalDate.now().minusMonths(months);
            }
            if (period.endsWith("y")) {
                int years = Integer.parseInt(period.substring(0, period.length() - 1));
                return LocalDate.now().minusYears(years);
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("지원하지 않는 기간 형식: " + period + " (예: 1m, 3m, 6m, 1y)");
    }

    @DeleteMapping("/{order_id}")
    public ResponseEntity<?> orderCancel(@PathVariable("order_id") Long orderId) {
        log.info("주문 취소 요청 수신: 주문 ID {}", orderId);

        try {
            orderService.requestCancel(orderId, "사용자 요청");

//            ResponseEntity.ok();

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(orderId + "번 주문 취소 요청이 접수되었습니다.");

        } catch (IllegalArgumentException e) {
            log.warn("취소할 주문을 찾을 수 없음: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("해당 주문을 찾을 수 없습니다.");
        } catch (IllegalStateException e) {
            log.warn("취소 요청 불가: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            log.error("주문 취소 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("취소 처리 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/{order_id}/cs-history")
    public ResponseEntity<?> csHistory(@PathVariable("order_id") Long orderNumber) {
        log.info("주문 취소/교환/반품 내역 조회 요청: 주문 ID {}", orderNumber);

        try {
            List<OrderSummaryResponse> response = orderService.getCsHistory(orderNumber);
            if (response.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("주문을 찾을 수 없음: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("해당 주문을 찾을 수 없습니다.");
        } catch (Exception e) {
            log.error("주문 취소/교환/반품 내역 조회 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("주문 취소/교환/반품 내역 조회 실패");
        }
    }

    @GetMapping("/{order_id}")
    public ResponseEntity<?> orderDetail(@PathVariable("order_id") Long orderNumber) {
        log.info("주문 상세 조회 요청: 주문 ID {}", orderNumber);

        try {
            OrderDetailResponse response = orderService.getOrderDetail(orderNumber);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("주문을 찾을 수 없음: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("해당 주문을 찾을 수 없습니다.");
        } catch (Exception e) {
            log.error("주문 상세 조회 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("주문 상세 조회 실패");
        }
    }
}