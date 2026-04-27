package com.eum.productserver.controller;

import com.eum.productserver.dto.request.CheckoutValidationRequest;
import com.eum.productserver.dto.response.CheckoutValidationResponse;
import com.eum.productserver.service.ProductCheckoutValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 생성 직전에 orderserver가 호출하는 checkout 검증 API입니다.
 *
 * 여기서는 상품/옵션/가격/판매 상태만 검증하고, 실제 재고 부족 여부는 이후 inventoryserver의 예약 단계에서 판단합니다.
 */
@RestController
@RequestMapping("/product/checkout")
@RequiredArgsConstructor
public class ProductCheckoutController {

    private final ProductCheckoutValidationService checkoutValidationService;

    // 주문 대상 상품의 판매 가능 여부와 가격 스냅샷을 확정합니다.
    // 재고 수량은 inventoryserver가 최종 판단하므로 여기서는 상품/옵션/가격/판매상태만 검증합니다.
    @PostMapping("/validate")
    public ResponseEntity<CheckoutValidationResponse> validate(@RequestBody CheckoutValidationRequest request) {
        return ResponseEntity.ok(checkoutValidationService.validate(request));
    }
}
