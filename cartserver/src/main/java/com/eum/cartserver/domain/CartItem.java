package com.eum.cartserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "item_id", nullable = false)
    private Long productId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "is_selected", nullable = false)
    private boolean selected = true;

    public CartItem(Cart cart, Long productId, Long optionId, Long quantity) {
        this.cart = cart;
        this.productId = productId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    public void updateQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public void updateOption(Long optionId) {
        this.optionId = optionId;
    }

    public void updateSelected(boolean selected) {
        this.selected = selected;
    }
}
