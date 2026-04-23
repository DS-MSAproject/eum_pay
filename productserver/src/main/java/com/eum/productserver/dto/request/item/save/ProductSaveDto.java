package com.eum.productserver.dto.request.item.save;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.Product.ProductStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSaveDto {

    // 1. 기본 정보
    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 100, message = "상품명은 최대 100자까지 가능합니다.")
    private String productName;

    @NotBlank(message = "상품 설명은 필수입니다.")
    private String content;

    private String imageUrl;
    private String productUrl;
    private String brandName;
    private Long brandId;
    private String tags;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Long price;

    private String status;

    private String keywords;

    @Min(value = 0, message = "배송비는 0원 이상이어야 합니다.")
    private Long deliveryFee;      // 배송비
    private String deliveryMethod; // 배송 방법

    private Long salesCount;       // 임의 판매량 (예: 5000)
    private Integer salesRank;     // 임의 순위 (1~3위 태그 결정용)


    private List<ProductOptionSaveDto> options;

    public Product toEntity() {
        return Product.builder()
                .productName(this.productName)
                .content(this.content)
                .imageUrl(this.imageUrl)
                .brandName(this.brandName)
                .brandId(this.brandId)
                .price(this.price)
                .status(parseStatus(this.status))
                .tags(this.tags)
                .keywords(this.keywords)
                .salesCount(0L)
                .salesRank(0)
                .deliveryFee(this.deliveryFee != null ? this.deliveryFee.longValue() : 0L)
                .deliveryMethod(this.deliveryMethod)
                .build();
    }

    private ProductStatus parseStatus(String status) {
        try {
            return (status != null) ? ProductStatus.valueOf(status) : ProductStatus.판매중;
        } catch (IllegalArgumentException e) {
            return ProductStatus.판매중; // 잘못된 값이 들어오면 기본값 설정
        }
    }
}
