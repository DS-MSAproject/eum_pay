package com.eum.productserver.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 생성을 막기 위해 접근 제어
@AllArgsConstructor
@Builder
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String imageUrl; // S3에서 제공하는 전체 경로 URL

    @Column(nullable = false, length = 1000)
    private String imageKey; // S3 삭제/관리용 Key

    private boolean isMain; // 대표 이미지 여부

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;


    public void setProduct(Product product) {
        this.product = product;
        // 만약 부모 객체의 리스트에 내가 없다면 추가해주는 로직을 넣기도 합니다.
        if (product != null && !product.getImages().contains(this)) {
            product.getImages().add(this);
        }
    }



    // 생성자 (필요 시)
    public  ProductImage(String imageUrl, String imageKey, boolean isMain, Product product) {
        this.imageUrl = imageUrl;
        this.imageKey = imageKey;
        this.isMain = isMain;
        this.product = product;
    }

}
