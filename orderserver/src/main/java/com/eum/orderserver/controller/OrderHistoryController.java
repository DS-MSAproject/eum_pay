package com.eum.orderserver.controller;

import com.eum.orderserver.dto.OrderItemHistoryResponse;
import com.eum.orderserver.service.OrderHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderHistoryController {

    private final OrderHistoryService historyService;

    /**
     * 로그인 사용자의 전체 주문 내역 조회
     */
    @GetMapping("/me/history")
    public ResponseEntity<List<OrderItemHistoryResponse>> myHistory(
            @RequestHeader("X-User-Id") Long userId
    ) {
        log.info("사용자 {}의 전체 주문 내역 조회 요청", userId);
        return getHistoryResponse(userId);
    }

    /**
     * 특정 사용자의 전체 주문 내역 조회
     */
    @Deprecated
    @GetMapping("/history/{user_id}")
    public ResponseEntity<List<OrderItemHistoryResponse>> historyList(@PathVariable("user_id") Long userId) {
        log.warn("Deprecated API called: GET /orders/history/{user_id}");
        return getHistoryResponse(userId);
    }

    private ResponseEntity<List<OrderItemHistoryResponse>> getHistoryResponse(Long userId) {
        try {
            List<OrderItemHistoryResponse> allHistory = historyService.getAllHistory(userId);

            if (allHistory.isEmpty()) {
                log.warn("사용자 {}의 주문 내역이 존재하지 않습니다.", userId);
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(allHistory);
        } catch (Exception e) {
            log.error("주문 내역 조회 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
