package com.eum.productserver.entity;

import com.eum.productserver.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.lang.Nullable;

@Getter
@Setter
@Entity
@Table(name = "product_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long id;

    @Column(nullable = true)
    @Nullable
    private String optionName; // "오독오독 1kg" 등

    @Builder.Default
    private Long extraPrice = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // 옵션 정보 수정 메서드 (서비스에서 호출용)
    public void updateOption(@Nullable String optionName, Long extraPrice) {
        this.optionName = optionName;
        this.extraPrice = extraPrice;
    }

    // 연관관계 편의 메서드
    public void setProduct(Product product) {
        this.product = product;
        if (product != null && !product.getOptions().contains(this)) {
            product.getOptions().add(this);
        }
    }
}
