package com.eum.productserver.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

/**
 * -Response
 * 상품 상세 페이지나 수정 페이지에서 개별 옵션 정보를 보여줄 때 사용
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResProductOptionDetail {

    private Long optionId;
    @Nullable
    private String optionName;
    private Long extraPrice;

    private Long productId;
    private String productName;

    /**
     * Entity -> DTO 변환 메서드
     * Product는 옵션 정의만 반환합니다.
     */
    public static ResProductOptionDetail fromEntity(com.eum.productserver.entity.ProductOption entity) {
        return ResProductOptionDetail.builder()
                .optionId(entity.getId())
                .optionName(entity.getOptionName())
                .extraPrice(entity.getExtraPrice())
                .productId(entity.getProduct().getProductId())
                .productName(entity.getProduct().getProductName())
                .build();
    }
}
