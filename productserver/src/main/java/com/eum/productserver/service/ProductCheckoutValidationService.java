package com.eum.productserver.service;

import com.eum.productserver.dto.request.CheckoutValidationRequest;
import com.eum.productserver.dto.response.CheckoutValidationResponse;
import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductOption;
import com.eum.productserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * orderserver가 보낸 checkout item을 현재 상품 기준의 주문 스냅샷으로 검증/변환하는 서비스입니다.
 *
 * 상품명, 옵션명, 최종 가격은 여기서 확정하지만 재고 수량은 inventoryserver 예약 단계에서 최종 확인합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCheckoutValidationService {

    private static final long NO_OPTION_ID = 0L;

    private final ProductRepository productRepository;

    // 사진의 "최종 상품/가격/판매상태 재검증" 단계입니다.
    // 주문 서버가 넘긴 cart item 목록을 현재 상품 데이터 기준의 주문 스냅샷으로 변환합니다.
    public CheckoutValidationResponse validate(CheckoutValidationRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("주문 상품 목록이 비어 있습니다.");
        }

        LocalDateTime capturedAt = LocalDateTime.now();
        List<CheckoutValidationResponse.Item> items = request.getItems().stream()
                .map(item -> validateItem(item, capturedAt))
                .toList();
        Long totalPrice = items.stream()
                .mapToLong(item -> item.getLineTotalPrice() != null ? item.getLineTotalPrice() : 0L)
                .sum();

        return CheckoutValidationResponse.builder()
                .items(items)
                .totalPrice(totalPrice)
                .capturedAt(capturedAt)
                .build();
    }

    // 단일 주문 항목을 검증하고 결제/주문 상세에 저장할 상품명, 옵션명, 최종 가격을 계산합니다.
    // 재고 부족 여부는 여기서 판단하지 않고 inventoryserver의 예약 단계에 맡깁니다.
    private CheckoutValidationResponse.Item validateItem(CheckoutValidationRequest.Item item, LocalDateTime capturedAt) {
        if (item.getProductId() == null) {
            throw new IllegalArgumentException("productId는 필수입니다.");
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다. productId=" + item.getProductId());
        }

        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. productId=" + item.getProductId()));

        if (!product.isActiveForProjection()) {
            throw new IllegalStateException("현재 주문할 수 없는 상품입니다. productId=" + product.getProductId());
        }

        ProductOption option = resolveOption(product, item.getOptionId());
        Long extraPrice = option != null && option.getExtraPrice() != null ? option.getExtraPrice() : 0L;
        Long unitPrice = resolveProductPrice(product) + extraPrice;
        Long lineTotalPrice = unitPrice * item.getQuantity();

        // 이 응답값이 주문 스냅샷이 됩니다. 이후 상품 가격이 바뀌어도 해당 주문은 이 가격으로 진행됩니다.
        return CheckoutValidationResponse.Item.builder()
                .productId(product.getProductId())
                .optionId(toExternalOptionId(option))
                .productName(product.getProductName())
                .optionName(option != null ? option.getOptionName() : null)
                .price(unitPrice)
                .extraPrice(extraPrice)
                .quantity(item.getQuantity())
                .lineTotalPrice(lineTotalPrice)
                .capturedAt(capturedAt)
                .build();
    }

    // 옵션이 있는 주문이면 해당 옵션이 실제로 그 상품에 속하는지 확인합니다.
    // 다른 상품의 optionId를 섞어서 보내는 요청을 이 단계에서 차단합니다.
    private ProductOption resolveOption(Product product, Long optionId) {
        if (isNoOptionId(optionId)) {
            return null;
        }

        return product.getOptions().stream()
                .filter(option -> optionId.equals(option.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "상품에 속하지 않은 옵션입니다. productId=" + product.getProductId() + ", optionId=" + optionId));
    }

    private boolean isNoOptionId(Long optionId) {
        return optionId == null || optionId == NO_OPTION_ID;
    }

    private Long toExternalOptionId(ProductOption option) {
        return option != null ? option.getId() : NO_OPTION_ID;
    }

    private Long resolveProductPrice(Product product) {
        return product.getPrice();
    }
}
