package com.eum.productserver.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * orderserver가 productserver에 주문 전 상품 검증을 요청할 때 보내는 payload입니다.
 *
 * itemId/item_id, amount 같은 과거 필드명도 JsonAlias로 받아 기존 요청 형식과 호환합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutValidationRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("productId")
        @JsonAlias({"product_id", "itemId", "item_id"})
        private Long productId;

        @JsonProperty("optionId")
        @JsonAlias({"option_id"})
        private Long optionId;

        @JsonProperty("quantity")
        @JsonAlias({"amount"})
        private Long quantity;
    }
}
