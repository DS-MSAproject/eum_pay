package com.eum.productserver.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * -Response
 * 주문에서 Item 정보 요청
 * @author 김민규
 * @response itemId, itemName, itemPrice
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResProductOrder {

    @JsonProperty("productId")
    private Long productId;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("productImage")
    private String productImage; // 대표 이미지 URL

    @JsonProperty("price")
    private Long price;   // 상품 기본가

    @JsonProperty("options")
    private List<OptionInfo> options;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionInfo {
        @JsonProperty("optionId")
        private Long optionId;

        @JsonProperty("optionName")
        @Nullable
        private String optionName;

        @JsonProperty("extraPrice")
        private Long extraPrice; // 옵션 추가 금액
    }

    // 결제 금액 계산 로직은 서비스 레이어에서 처리하는 것을 권장하지만,
    // DTO에서 참조용으로 남겨둘 수 있습니다.
    public Long getTotalBasePrice() {
        return (price != null) ? price : 0;
    }
}
