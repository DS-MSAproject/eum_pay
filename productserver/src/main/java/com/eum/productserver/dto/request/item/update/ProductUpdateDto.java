package com.eum.productserver.dto.request.item.update;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * -Request
 * 판매자 상품 수정 정보
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductUpdateDto {

    @NotNull(message = "수정할 상품 ID는 필수입니다.")
    private Long productId; // 어떤 상품을 고칠지 알려주는 ID 추가

    // 카테고리 수정이 필요할 경우 추가
    private Long categoryId;

    // 상품 관련 정보
    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 50, message = "상품명은 최대 50자까지 가능합니다.")
    private String productName;
    @NotBlank(message = "상품 설명은 필수입니다.")
    @Size(max = 2000, message = "상품 설명은 최대 2000자까지 가능합니다.")
    private String content;
    private String productUrl;
    private String brandName;
    private Long brandId;
    private String tags;
    private String keywords;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Long price;

    private String status;

    private Long deliveryFee;      // 배송비
    private String deliveryMethod; // 배송 방법


    private List<ProductOptionUpdateDto> options;

}
