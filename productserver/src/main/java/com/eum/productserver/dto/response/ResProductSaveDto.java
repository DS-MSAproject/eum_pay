package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductDetailImage;
import lombok.*;

import java.util.List;

/**
 * -Response
 * 판매자 상품 등록 정보
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResProductSaveDto {

    private Long productId;    // 등록된 상품 ID
    private String productName;  // 상품 제목
    private List<String> detailImageUrls;
    private String productUrl;
    private String brandName;
    private Long price;        // 가격
    private String priceDisplay; // 가격 표시
    private String status;      // 판매 상태 (판매중, 예약중 등)

    private Long deliveryFee;
    private String deliveryMethod;

    // 등록된 옵션들 명칭
    private List<ResProductOptionDto> options;

    public static ResProductSaveDto fromEntity(Product product) {
        if (product == null) return null;

        String formattedPrice = (product.getPrice() != null)
                ? String.format("%,d원", product.getPrice())
                : "0원";

        return ResProductSaveDto.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .detailImageUrls(resolveDetailImageUrls(product))
                .productUrl(product.getProductUrl())
                .brandName(product.getBrandName())
                .price(product.getPrice())
                .priceDisplay(formattedPrice)
                .status(product.getStatus() != null ? product.getStatus().name() : "판매중")
                .deliveryFee(product.getDeliveryFee())
                .deliveryMethod(product.getDeliveryMethod())
                .options(product.getOptions().stream()
                        .map(ResProductOptionDto::fromEntity)
                        .toList())
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
