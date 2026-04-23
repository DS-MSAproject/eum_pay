package com.eum.inventoryserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 예약에 포함된 상품/옵션별 예약 항목입니다.
 *
 * productName, optionName, price는 재고 계산용 값이 아니라 주문 상세 스냅샷으로
 * orderserver에 넘기기 위해 예약 시점의 상품 정보를 함께 보관합니다.
 */
@Getter
@Entity
@Table(name = "inventory_reservation_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_reservation_item", columnNames = {"reservation_id", "product_id", "option_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "option_name")
    private String optionName;

    @Column(name = "price")
    private Long price;

    private InventoryReservationItem(Long productId, Long optionId, Integer quantity,
                                     String productName, String optionName, Long price) {
        this.productId = productId;
        this.optionId = optionId;
        this.quantity = quantity;
        this.productName = productName;
        this.optionName = optionName;
        this.price = price;
    }

    public static InventoryReservationItem of(Long productId, Long optionId, Integer quantity) {
        return new InventoryReservationItem(productId, optionId, quantity, null, null, null);
    }

    public static InventoryReservationItem of(Long productId, Long optionId, Integer quantity,
                                              String productName, String optionName, Long price) {
        return new InventoryReservationItem(productId, optionId, quantity, productName, optionName, price);
    }

    void attach(InventoryReservation reservation) {
        this.reservation = reservation;
    }
}
