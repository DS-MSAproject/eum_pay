package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductDetailImage;
import lombok.*;

import java.util.List;

/**
 * -Response
 * 판매자 상품 수정 정보
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResProductUpdateDto {

    private Long productId;
    private String productName;   // titleName -> productName
    private List<String> detailImageUrls;
    private String brandName;
    private Long price;         // Long
    private String status;         // state -> status (ProductStatus)
    private Long deliveryFee;
    private String deliveryMethod;

    // 수정된 옵션 리스트도 확인용으로 내려주면 좋습니다.
    private List<ResProductOptionDto> options;

    public static ResProductUpdateDto fromEntity(Product product) {
        if (product == null) return null;

        return ResProductUpdateDto.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .detailImageUrls(resolveDetailImageUrls(product))
                .brandName(product.getBrandName())
                .price(product.getPrice())
                .status(product.getStatus() != null ? product.getStatus().name() : "판매중")
                .deliveryFee(product.getDeliveryFee())
                .deliveryMethod(product.getDeliveryMethod())
                // 옵션 리스트 변환
                .options(product.getOptions() != null ?
                        product.getOptions().stream()
                                .map(ResProductOptionDto::fromEntity)
                                .toList() : null)
                .build();
    }

    private static List<String> resolveDetailImageUrls(Product product) {
        return product.getDetailImages() != null
                ? product.getDetailImages().stream()
                .sorted((left, right) -> Integer.compare(
                        left.getDisplayOrder() != null ? left.getDisplayOrder() : 0,
                        right.getDisplayOrder() != null ? right.getDisplayOrder() : 0
                ))
                .map(ProductDetailImage::getImageUrl)
                .toList()
                : List.of();
    }
}
