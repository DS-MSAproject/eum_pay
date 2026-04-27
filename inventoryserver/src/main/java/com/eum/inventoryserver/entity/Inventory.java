package com.eum.inventoryserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * inventoryserver의 실제 재고 원장 엔티티입니다.
 *
 * productId와 optionId는 productserver의 상품/옵션을 참조하는 값이고,
 * stockQuantity는 주문 예약 시 차감되고 결제 실패/환불 보상 시 복구되는 현재 가용 재고입니다.
 */
@Entity
@Table(name = "inventory", indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId; // product 서버의 상품 ID

    @Column(name = "option_id")
    private Long optionId;

    @Column(nullable = false)
    private Integer stockQuantity; // 현재 가용 재고

    // 재고 증가
    public void addStock(int quantity) {
        this.stockQuantity += quantity;
    }

    // 재고 차감 (출고/주문)
    public void removeStock(int quantity) {
        int restStock = this.stockQuantity - quantity;
        if (restStock < 0 ) {
            throw new IllegalArgumentException("재고가 부족합니다. (현재 재고: " + this.stockQuantity + ")");
        }
        this.stockQuantity = restStock;
    }
}
