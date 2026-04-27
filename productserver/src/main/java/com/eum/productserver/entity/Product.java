package com.eum.productserver.entity;

import com.eum.productserver.common.BaseTimeEntity;
import com.eum.productserver.dto.request.item.update.ProductUpdateDto;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter
@Entity
@Table(name = "Products") // PostgreSQL 예약어 회피를 위해 복수형 권장
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseTimeEntity {

    public enum ProductStatus {
        판매중, 판매중지
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(length = 1000)
    private String productUrl;

    private String brandName;
    private Long brandId;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    private ProductStatus status; // 판매중, 일시품절, 판매중지 등

    @Builder.Default
    private Long salesCount = 0L; //판매수량

    private String tags;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOption> options = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductDetailImage> detailImages = new ArrayList<>();

    // 추가해야 할 필드들
    private Integer salesRank; // 💡 검색 서버의 [판매 1~3위] 태그용
    private String keywords;   // 💡 가중치 검색을 위한 키워드

    // --- 배송 관련 필드 추가 ---
    @Builder.Default
    private Long deliveryFee = 0L;      // 배송비
    @Builder.Default
    private String deliveryMethod = "일반택배"; // 배송 방법

    // 추가해야 할 메서드
    public void updateSalesRank(Integer rank) {
        this.salesRank = rank;
    }

    // 판매량 증가
    public void increaseSalesCount(int quantity) {
        this.salesCount += quantity;
    }

    public boolean isActiveForProjection() {
        return this.status == ProductStatus.판매중;
    }

    // 이미지 추가
    public void addImage(ProductImage productImage) {
        this.images.add(productImage);
        if (productImage.getProduct() != this) {
            productImage.setProduct(this);
        }
    }

    public void addDetailImage(ProductDetailImage productDetailImage) {
        this.detailImages.add(productDetailImage);
        if (productDetailImage.getProduct() != this) {
            productDetailImage.setProduct(this);
        }
    }

    // 옵션 추가
    public void addOption(ProductOption productOption) {
// 이미 포함되어 있다면 추가하지 않는 방어 코드
        if (this.options.contains(productOption)) {
            return;
        }
        this.options.add(productOption);
        productOption.setProduct(this);
    }

    // 상품 정보 업데이트
    public void updateProduct(Category category, ProductUpdateDto dto) {
        this.category = category;
        this.productName = dto.getProductName();
        this.content = dto.getContent();
        this.brandName = dto.getBrandName();
        this.brandId = dto.getBrandId();
        this.price = dto.getPrice();
        this.tags = dto.getTags();
        this.keywords = dto.getKeywords();
        this.deliveryFee = dto.getDeliveryFee() != null ? dto.getDeliveryFee() : 0L;
        this.deliveryMethod = dto.getDeliveryMethod();

        if (dto.getStatus() != null) {
            try {
                ProductStatus requestedStatus = ProductStatus.valueOf(dto.getStatus());
                this.status = requestedStatus;
            } catch (IllegalArgumentException e) {
                this.status = ProductStatus.판매중지; // 혹은 기본값 설정
            }
        }
    }

}


