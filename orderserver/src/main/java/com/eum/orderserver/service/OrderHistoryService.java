package com.eum.orderserver.service;

import com.eum.orderserver.domain.OrderDetails;
import com.eum.orderserver.domain.Orders;
import com.eum.orderserver.dto.OrderItemHistoryResponse;
import com.eum.orderserver.dto.product.CheckoutValidationResponse;
import com.eum.orderserver.repository.OrderDetailsRepository;
import com.eum.orderserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderHistoryService {

    private final OrderDetailsRepository orderDetailsRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void saveValidatedItems(Long orderId, Long userId, List<CheckoutValidationResponse.Item> orderItems) {
        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문을 찾을 수 없습니다. ID:" + orderId));

        List<OrderDetails> detailsList = orderItems.stream()
                .map(item -> {
                    Long lineTotalPrice = item.getLineTotalPrice();
                    if (lineTotalPrice == null) {
                        throw new IllegalStateException("Product checkout lineTotalPrice is required.");
                    }

                    return OrderDetails.builder()
                            .orders(order)
                            .userId(userId)
                            .productId(item.getProductId())
                            .optionId(item.getOptionId())
                            .price(item.getPrice())
                            .quantity(item.getQuantity())
                            .totalPrice(lineTotalPrice)
                            .snapshotJson(OrderDetails.checkoutSnapshot(
                                    item.getProductId(),
                                    item.getOptionId(),
                                    item.getProductName(),
                                    item.getOptionName(),
                                    item.getPrice(),
                                    item.getQuantity(),
                                    item.getDiscountPrice(),
                                    item.getExtraPrice(),
                                    lineTotalPrice,
                                    item.getCapturedAt()
                            ))
                            .build();
                })
                .toList();

        orderDetailsRepository.saveAll(detailsList);
        log.info("{}번 주문 Product 검증 항목 {}건 저장 완료", orderId, detailsList.size());
    }

    public List<OrderItemHistoryResponse> getAllHistory(Long userId) {
        List<OrderDetails> allByUserId = orderDetailsRepository.findAllByUserId(userId);

        return allByUserId.stream().map(orderDetail ->
                OrderItemHistoryResponse.builder()
                        .id(orderDetail.getId())
                        .userId(orderDetail.getUserId())
                        .orderId(orderDetail.getOrders().getOrderId())
                        .productId(orderDetail.getProductId())
                        .optionId(orderDetail.getOptionId())
                        .productName(orderDetail.getProductName())
                        .optionName(orderDetail.getOptionName())
                        .price(orderDetail.getPrice())
                        .quantity(orderDetail.getQuantity())
                        .totalPrice(orderDetail.getTotalPrice())
                        .orderState(orderDetail.getOrders().getOrderState())
                        .failedReason(orderDetail.getOrders().getFailedReason())
                        .failedAt(orderDetail.getOrders().getFailedAt())
                        .build()
        ).toList();
    }
}