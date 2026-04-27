package com.eum.orderserver.service;

import com.eum.common.correlation.Correlated;
import com.eum.common.correlation.CorrelationIdSource;
import com.eum.orderserver.client.ProductCheckoutClient;
import com.eum.orderserver.domain.OrderDetails;
import com.eum.orderserver.domain.OrderState;
import com.eum.orderserver.domain.Orders;
import com.eum.orderserver.dto.OrderDetailResponse;
import com.eum.orderserver.dto.OrderRequest;
import com.eum.orderserver.dto.OrderSummaryResponse;
import com.eum.orderserver.dto.product.CheckoutValidationRequest;
import com.eum.orderserver.dto.product.CheckoutValidationResponse;
import com.eum.orderserver.message.inventory.InventoryDeductionEvent;
import com.eum.orderserver.message.inventory.InventoryReleaseEvent;
import com.eum.orderserver.message.inventory.InventoryReservationEvent;
import com.eum.orderserver.message.payment.PaymentOrderEvent;
import com.eum.orderserver.outbox.OrderOutboxService;
import com.eum.orderserver.repository.OrderDetailsRepository;
import com.eum.orderserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryService orderHistoryService;
    private final OrderDetailsRepository orderDetailsRepository;
    private final OrderOutboxService outboxService;
    private final ProductCheckoutClient productCheckoutClient;

    @Correlated
    @Transactional
    public Orders register(@CorrelationIdSource String correlationId, OrderRequest request, Long userId) {
        validateOrderRequest(request);

        CheckoutValidationResponse productValidation = validateProducts(request);
        List<CheckoutValidationResponse.Item> validatedItems = productValidation.getItems();
        Long totalAmount = calculateTotalAmount(productValidation);

        Orders order = Orders.builder()
                .userId(userId)
                .userName(request.getUserName())
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .receiverAddr(request.getReceiverAddr())
                .amount(totalAmount)
                .orderState(OrderState.ORDER_CHECKED_OUT)
                .time(LocalDateTime.now())
                .deleteYn("N")
                .build();

        Orders savedOrder = orderRepository.save(order);
        orderHistoryService.saveValidatedItems(savedOrder.getOrderId(), userId, validatedItems);
        outboxService.enqueueOrderCheckedOut(
                savedOrder.getOrderId(),
                userId,
                request.getReceiverName(),
                request.getReceiverPhone(),
                request.getReceiverAddr(),
                totalAmount,
                productValidation.getTotalPrice(),
                productValidation.getCapturedAt(),
                validatedItems
        );

        return savedOrder;
    }

    @Transactional
    public void handleInventoryReserved(InventoryReservationEvent event) {
        Orders order = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + event.getOrderId()));

        if (order.getOrderState() != OrderState.ORDER_CHECKED_OUT) {
            log.info("{}번 주문은 재고 예약 완료 처리 대상 상태가 아닙니다: {}", order.getOrderId(), order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.INVENTORY_RESERVED);
        log.info("{}번 주문 재고 예약 완료", order.getOrderId());
    }

    @Transactional
    public void handleInventoryReservationFailed(InventoryReservationEvent event) {
        Orders order = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + event.getOrderId()));

        if (order.getOrderState() != OrderState.ORDER_CHECKED_OUT) {
            log.info("{}번 주문은 재고 예약 실패 처리 대상 상태가 아닙니다: {}", event.getOrderId(), order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.INVENTORY_RESERVATION_FAILED);
        log.warn("{}번 주문 재고 예약 실패: {}", event.getOrderId(), event.getReason());
    }

    @Transactional
    public void handlePaymentCompleted(PaymentOrderEvent event) {
        Long orderId = event.getOrderId();
        log.info("{}번 주문 결제 완료 이벤트 처리 시도", orderId);

        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + orderId));

        if (order.getOrderState() != OrderState.INVENTORY_RESERVED) {
            log.info("{}번 주문은 결제 완료 처리 대상 상태가 아닙니다: {}", orderId, order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.PAYMENT_COMPLETED);
        order.setPaymentMethod(event.getPaymentMethod());
        order.setPaidAmount(event.getPaidAmount() != null ? event.getPaidAmount() : event.getAmount());
        log.info("{}번 주문 상태 변경 완료 : {}", orderId, order.getOrderState());
    }

    @Transactional
    public void handlePaymentFailed(PaymentOrderEvent event) {
        Long orderId = event.getOrderId();
        log.info("{}번 주문 결제 실패 이벤트 처리 시도", orderId);

        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + orderId));

        if (order.getOrderState() == OrderState.INVENTORY_RELEASED
                || order.getOrderState() == OrderState.INVENTORY_RELEASE_FAILED) {
            log.info("{}번 주문은 이미 재고 예약 해제 이벤트 체이닝 상태입니다: {}", orderId, order.getOrderState());
            return;
        }

        if (order.getOrderState() == OrderState.ORDER_COMPLETED) {
            log.warn("{}번 완료 주문에 결제 실패 이벤트가 도착했습니다.", orderId);
            return;
        }

        if (order.getOrderState() != OrderState.INVENTORY_RESERVED) {
            log.info("{}번 주문은 결제 실패 처리 대상 상태가 아닙니다: {}", orderId, order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.PAYMENT_FAILED);
        order.setFailedReason(event.getFailureReason());
        order.setFailedAt(event.getFailedAt() != null ? event.getFailedAt() : LocalDateTime.now());

        log.info("{}번 주문 결제 실패 처리 완료: {}", orderId, event.getFailureReason());
    }

    @Transactional
    public void handleInventoryDeducted(InventoryDeductionEvent event) {
        Orders order = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + event.getOrderId()));

        if (order.getOrderState() == OrderState.ORDER_COMPLETED) {
            log.info("{}번 주문은 이미 완료 상태입니다.", order.getOrderId());
            return;
        }

        if (order.getOrderState() != OrderState.PAYMENT_COMPLETED) {
            log.warn("{}번 주문은 재고 차감 완료 처리 대상 상태가 아닙니다: {}", order.getOrderId(), order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.ORDER_COMPLETED);
        outboxService.enqueueOrderCompleted(order.getOrderId(), order.getUserId(), order.getAmount());
        log.info("{}번 주문 재고 차감 완료, 주문 완료 이벤트 저장", order.getOrderId());
    }

    @Transactional
    public void handleInventoryDeductionFailed(InventoryDeductionEvent event) {
        Orders order = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + event.getOrderId()));

        if (order.getOrderState() != OrderState.PAYMENT_COMPLETED) {
            log.info("{}번 주문은 재고 차감 실패 처리 대상 상태가 아닙니다: {}", event.getOrderId(), order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.INVENTORY_DEDUCTION_FAILED);
        log.warn("{}번 주문 재고 차감 실패: {}", event.getOrderId(), event.getReason());
    }

    @Transactional
    public void handleInventoryReleased(InventoryReleaseEvent event) {
        Orders order = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + event.getOrderId()));

        if (order.getOrderState() == OrderState.INVENTORY_RELEASED) {
            log.info("{}번 주문은 이미 재고 예약 해제 완료 상태입니다.", order.getOrderId());
            return;
        }

        if (order.getOrderState() != OrderState.PAYMENT_FAILED
                && order.getOrderState() != OrderState.ORDER_CANCELLED) {
            log.warn("{}번 주문은 재고 해제 처리 대상 상태가 아닙니다: {}", order.getOrderId(), order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.INVENTORY_RELEASED);
        log.info("{}번 주문 재고 예약 해제 완료", event.getOrderId());
    }

    @Transactional
    public void handleInventoryReleaseFailed(InventoryReleaseEvent event) {
        Orders order = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + event.getOrderId()));

        if (order.getOrderState() != OrderState.PAYMENT_FAILED
                && order.getOrderState() != OrderState.ORDER_CANCELLED) {
            log.warn("{}번 주문은 재고 해제 실패 처리 대상 상태가 아닙니다: {}", order.getOrderId(), order.getOrderState());
            return;
        }

        order.setOrderState(OrderState.INVENTORY_RELEASE_FAILED);
        log.warn("{}번 주문 재고 예약 해제 실패: {}", event.getOrderId(), event.getReason());
    }

    @Correlated
    @Transactional
    public void requestCancel(@CorrelationIdSource String correlationId, Long orderId, String reason) {
        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + orderId));

        if (order.getOrderState() != OrderState.ORDER_COMPLETED
                && order.getOrderState() != OrderState.PAYMENT_COMPLETED) {
            throw new IllegalStateException(
                    "취소 가능한 상태가 아닙니다. 현재 상태: " + order.getOrderState().getState());
        }

        order.setOrderState(OrderState.ORDER_CANCELLED);
        outboxService.enqueueOrderCancelled(order.getOrderId(), order.getUserId(), reason);
        log.info("{}번 주문 취소 요청. reason={}", orderId, reason);
    }

    @Transactional(readOnly = true)
    public Orders selectOrder(Long orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. ID: " + orderId));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId) {
        Orders order = selectOrder(orderId);
        List<OrderDetails> details = orderDetailsRepository.findAllByOrdersOrderIdOrderByIdAsc(orderId);
        Long totalItemCount = details.stream()
                .map(OrderDetails::getQuantity)
                .mapToLong(quantity -> quantity != null ? quantity : 0L)
                .sum();

        List<OrderDetailResponse.Item> items = details.stream()
                .map(detail -> OrderDetailResponse.Item.builder()
                        .productId(detail.getProductId())
                        .optionId(detail.getOptionId())
                        .productName(detail.getProductName())
                        .optionName(detail.getOptionName())
                        .price(detail.getPrice())
                        .quantity(detail.getQuantity())
                        .totalPrice(detail.getTotalPrice())
                        .build())
                .toList();

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .userName(order.getUserName())
                .amount(order.getAmount())
                .totalItemCount(totalItemCount)
                .productTotalPrice(order.getAmount())
                .paymentMethod(order.getPaymentMethod())
                .paidAmount(order.getPaidAmount())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .receiverAddr(order.getReceiverAddr())
                .deleteYn(order.getDeleteYn())
                .time(order.getTime())
                .orderState(order.getOrderState())
                .failedReason(order.getFailedReason())
                .failedAt(order.getFailedAt())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listOrders(Long userId, LocalDate startDate, LocalDate endDate,
                                                 OrderState status, int page) {
        LocalDateTime start = (startDate == null) ? null : startDate.atStartOfDay();
        LocalDateTime end = (endDate == null) ? null : endDate.atTime(LocalTime.MAX);
        PageRequest pageable = PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "time"));

        return orderRepository.findOrders(userId, start, end, status, pageable)
                .map(order -> OrderSummaryResponse.builder()
                        .orderId(order.getOrderId())
                        .userId(order.getUserId())
                        .amount(order.getAmount())
                        .receiverName(order.getReceiverName())
                        .receiverPhone(order.getReceiverPhone())
                        .receiverAddr(order.getReceiverAddr())
                        .deleteYn(order.getDeleteYn())
                        .time(order.getTime())
                        .orderState(order.getOrderState())
                        .failedReason(order.getFailedReason())
                        .failedAt(order.getFailedAt())
                        .build()
                );
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getCsHistory(Long orderId) {
        selectOrder(orderId);
        return List.of();
    }

    private void validateOrderRequest(OrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("주문 항목이 비어 있습니다.");
        }
    }

    private CheckoutValidationResponse validateProducts(OrderRequest request) {
        CheckoutValidationResponse response = productCheckoutClient.validate(
                CheckoutValidationRequest.from(request.getItems())
        );

        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            throw new IllegalStateException("Product checkout 검증 응답이 올바르지 않습니다.");
        }
        return response;
    }

    private Long calculateTotalAmount(CheckoutValidationResponse response) {
        if (response.getTotalPrice() == null) {
            throw new IllegalStateException("Product checkout totalPrice is required.");
        }

        Long itemTotalAmount = response.getItems().stream()
                .mapToLong(CheckoutValidationResponse.Item::totalPrice)
                .sum();

        if (!response.getTotalPrice().equals(itemTotalAmount)) {
            throw new IllegalStateException("Product checkout totalPrice mismatch. productTotalPrice="
                    + response.getTotalPrice() + ", itemTotalPrice=" + itemTotalAmount);
        }

        return response.getTotalPrice();
    }

}
