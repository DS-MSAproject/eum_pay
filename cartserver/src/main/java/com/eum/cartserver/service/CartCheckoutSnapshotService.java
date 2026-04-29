package com.eum.cartserver.service;

import com.eum.cartserver.client.OrderDetailClient;
import com.eum.cartserver.client.dto.OrderDetailDto;
import com.eum.cartserver.domain.CartCheckoutSnapshot;
import com.eum.cartserver.dto.CartItemDeleteRequest;
import com.eum.cartserver.message.OrderCheckedOutMessage;
import com.eum.cartserver.repository.CartCheckoutSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartCheckoutSnapshotService {

    private final CartCheckoutSnapshotRepository snapshotRepository;
    private final CartService cartService;
    private final ObjectMapper objectMapper;
    private final OrderDetailClient orderDetailClient;

    @Transactional
    public void saveSnapshot(Long orderId, Long userId, List<OrderCheckedOutMessage.Item> items) {
        if (snapshotRepository.findByOrderId(orderId).isPresent()) {
            log.debug("이미 저장된 스냅샷 — 중복 이벤트 무시. orderId={}", orderId);
            return;
        }
        try {
            String itemsJson = objectMapper.writeValueAsString(items);
            snapshotRepository.save(CartCheckoutSnapshot.of(orderId, userId, itemsJson));
            log.info("장바구니 체크아웃 스냅샷 저장. orderId={}, userId={}, itemCount={}", orderId, userId, items.size());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 직렬화 실패. orderId=" + orderId, e);
        }
    }

    @Transactional
    public void clearCartOnPaymentCompleted(Long orderId) {
        log.info("[clearCartOnPaymentCompleted] 스냅샷 조회 시작. orderId={}", orderId);
        snapshotRepository.findByOrderId(orderId).ifPresentOrElse(
                snapshot -> {
                    log.info("[clearCartOnPaymentCompleted] 스냅샷 발견. orderId={}, userId={}", orderId, snapshot.getUserId());
                    List<CartItemDeleteRequest> requests = deserializeItems(snapshot.getItems(), orderId);
                    log.info("[clearCartOnPaymentCompleted] 장바구니 항목 삭제 요청. userId={}, itemCount={}", snapshot.getUserId(), requests.size());
                    cartService.removeOrderedItems(snapshot.getUserId(), requests);
                    snapshotRepository.deleteByOrderId(orderId);
                    log.info("[clearCartOnPaymentCompleted] 완료 — 장바구니 삭제 및 스냅샷 제거. orderId={}, userId={}", orderId, snapshot.getUserId());
                },
                () -> log.warn("[clearCartOnPaymentCompleted] 스냅샷 없음 — 이미 처리됐거나 OrderCheckedOut 미수신. orderId={}", orderId)
        );
    }

    @Transactional
    public void clearSnapshotOnly(Long orderId, String reason) {
        snapshotRepository.findByOrderId(orderId).ifPresentOrElse(
                snapshot -> {
                    snapshotRepository.deleteByOrderId(orderId);
                    log.info("스냅샷 삭제 (장바구니 유지). orderId={}, reason={}", orderId, reason);
                },
                () -> log.debug("삭제할 스냅샷 없음. orderId={}", orderId)
        );
    }

    @Transactional
    public void handleOrderCancelled(Long orderId, Long userId) {
        snapshotRepository.findByOrderId(orderId).ifPresentOrElse(
                snapshot -> {
                    snapshotRepository.deleteByOrderId(orderId);
                    log.info("OrderCancelled — 스냅샷 삭제 (장바구니 유지). orderId={}", orderId);
                },
                () -> {
                    // 스냅샷 없음 = PaymentCompleted에서 이미 장바구니 삭제됨 → 복원
                    log.info("OrderCancelled — 스냅샷 없음, 장바구니 복원 시도. orderId={}, userId={}", orderId, userId);
                    try {
                        OrderDetailDto orderDetail = orderDetailClient.getOrderDetail(orderId);
                        List<OrderDetailDto.Item> items = orderDetail.getItems();
                        if (items == null || items.isEmpty()) {
                            log.warn("OrderCancelled — 주문 항목 없음, 복원 불가. orderId={}", orderId);
                            return;
                        }
                        cartService.restoreOrderedItems(userId, items);
                    } catch (Exception e) {
                        log.error("OrderCancelled — 장바구니 복원 실패. orderId={}, error={}", orderId, e.getMessage(), e);
                    }
                }
        );
    }

    private List<CartItemDeleteRequest> deserializeItems(String itemsJson, Long orderId) {
        try {
            List<OrderCheckedOutMessage.Item> items = objectMapper.readValue(
                    itemsJson, new TypeReference<>() {}
            );
            return items.stream()
                    .map(item -> new CartItemDeleteRequest(item.getProductId(), item.getOptionId()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 역직렬화 실패. orderId=" + orderId, e);
        }
    }
}
