package com.eum.productserver.dto.request;

import com.eum.productserver.entity.Category;
import com.eum.productserver.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestDto {

    private String productName;    // 엔티티 필드명(productName)에 맞춤
    private String content;
    private String brandName;
    private Long brandId;
    private Long price;

    private String status;         // "판매중", "품절" 등 문자열로 입력받음

    private String imageUrl;
    private String productUrl;
    private String tags;
    private String keywords;       // 가중치 검색용 키워드

    // 💡 인기도 관련 필드 (엔티티에 대응)
    private Long deliveryFee;      // 배송비
    private String deliveryMethod; // 배송 방법

    // 💡 [임시 데이터 대응] 오더 서버 완성 전까지 직접 입력받을 필드
    private Long salesCount;       // 임의 판매량 (예: 5000)
    private Integer salesRank;     // 임의 순위 (1~3위 태그 결정용)

    /**
     * DTO -> Entity 변환
     */
    public Product toEntity(Category category) {
        return Product.builder()
                .productName(this.productName)
                .content(this.content)
                .brandName(this.brandName)
                .brandId(this.brandId)
                .price(this.price)
                .status(this.status != null ? Product.ProductStatus.valueOf(this.status) : Product.ProductStatus.판매중)
                .imageUrl(this.imageUrl)
                .productUrl(this.productUrl)
                .tags(this.tags)
                .keywords(this.keywords)
                .salesCount(this.salesCount != null ? this.salesCount : 0L)
                .salesRank(this.salesRank)
                .deliveryFee(this.deliveryFee != null ? this.deliveryFee : 0L)
                .deliveryMethod(this.deliveryMethod)
                .category(category) // 연관관계 설정
                .build();
    }
}
