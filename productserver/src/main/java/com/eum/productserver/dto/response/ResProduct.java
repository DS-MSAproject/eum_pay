package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductImage;
import com.querydsl.core.annotations.QueryProjection;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResProduct {

    private Long productId;
    private String productName;    // titleName -> productName
    private String brandName;      // 추가
    private String imageUrl;       // 대표 이미지
    private Long price;         // 원가
    private String priceDisplay;   // 포맷팅된 가격 (예: 15,000원)
    private String status;         // 판매중, 품절 등
    private Long salesCount;       // 판매량 (인기순 정렬용)
    private List<String> tags;           // 상품 태그
    private Integer salesRank; // 💡 검색 서버의 [판매 1~3위] 태그용
    private String keywords;   // 💡 가중치 검색을 위한 키워드
    private Long deliveryFee;      // 배송비
    private String deliveryMethod; // 배송 방법
    @QueryProjection
    public ResProduct(Long productId, String productName, String brandName,
                      String imageUrl, Long price,
                      Product.ProductStatus status, Long salesCount, Long deliveryFee, String deliveryMethod,
                      String tags, Integer salesRank, String keywords) {
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.imageUrl = imageUrl;
        this.price = price;
        this.status = (status != null) ? status.name() : "판매중";
        this.salesCount = salesCount;
        this.deliveryFee = deliveryFee;
        this.deliveryMethod = deliveryMethod;
        this.tags = ProductTagResolver.fromRawTags(tags);
        this.salesRank = salesRank;
        this.keywords = keywords;
        this.priceDisplay = String.format("%,d원", price);
    }

    public static ResProduct fromEntity(Product product) {
        if (product == null) return null;

        // 📍 1. 대표 이미지(isMain)를 먼저 찾고, 없으면 첫 번째 이미지, 그것도 없으면 null
        String mainImageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            mainImageUrl = product.getImages().stream()
                    .filter(ProductImage::isMain) // 📍 엔티티의 isMain 필드 체크
                    .map(ProductImage::getImageUrl) // 📍 엔티티의 imageUrl 필드 추출
                    .findFirst()
                    .orElse(product.getImages().get(0).getImageUrl()); // 대표 설정 없으면 0번 인덱스
        }

        return ResProduct.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .brandName(product.getBrandName())
                .imageUrl(mainImageUrl)
                .price(product.getPrice())
                .priceDisplay(String.format("%,d원", product.getPrice()))
                .status(product.getStatus() != null ? product.getStatus().name() : "판매중")
                .salesCount(product.getSalesCount() != null ? product.getSalesCount() : 0L)
                .tags(ProductTagResolver.resolve(product))
                .salesRank(product.getSalesRank())
                .keywords(product.getKeywords())
                .deliveryFee(product.getDeliveryFee() != null ? product.getDeliveryFee() : 0L)
                .deliveryMethod(product.getDeliveryMethod())
                .build();
    }
}
