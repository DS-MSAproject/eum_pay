package com.eum.productserver.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * checkout 검증 후 orderserver가 주문 상세에 저장할 상품 스냅샷 응답입니다.
 *
 * 이후 상품명이나 가격이 바뀌어도 이미 생성된 주문은 이 응답의 이름/가격/옵션 정보를 기준으로 진행됩니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutValidationResponse {

    private List<Item> items;
    private Long totalPrice;
    private LocalDateTime capturedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("option_id")
        private Long optionId;

        @JsonProperty("product_name")
        private String productName;

        @JsonProperty("option_name")
        @Nullable
        private String optionName;

        private Long price;
        private Long extraPrice;
        private Long quantity;
        private Long lineTotalPrice;
        private LocalDateTime capturedAt;
    }
}
