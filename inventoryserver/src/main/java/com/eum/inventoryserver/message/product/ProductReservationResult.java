package com.eum.inventoryserver.message.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 주문 재고 예약 결과를 orderserver에 전달하기 위한 payload입니다.
 *
 * success가 true이면 주문 상세에 저장할 예약 상품 스냅샷(items)을 함께 전달합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReservationResult {

    @JsonProperty("order_id")
    private Long orderId;

    private String correlationId;
    private boolean success;
    private String reason;
    private List<ReservedItem> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservedItem {
        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("option_id")
        private Long optionId;

        @JsonProperty("product_name")
        private String productName;

        @JsonProperty("option_name")
        private String optionName;

        private Long price;
        private Long quantity;
    }
}
