package com.eum.cartserver.domain;

import com.eum.cartserver.exception.InvalidCartRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "is_selected", nullable = false)
    private boolean selected = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CartItem(Long productId, Long optionId, Long quantity) {
        this.productId = productId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    public void attachTo(Cart cart) {
        this.cart = cart;
    }

    public void detachFromCart() {
        this.cart = null;
    }

    public boolean hasBusinessKey(Long productId, Long optionId) {
        return this.productId.equals(productId) && this.optionId.equals(optionId);
    }

    public void increaseQuantity(Long quantity) {
        validateQuantity(quantity);
        this.quantity += quantity;
    }

    public void changeQuantity(Long quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public void changeOption(Long optionId) {
        this.optionId = optionId;
    }

    public void changeSelected(boolean selected) {
        this.selected = selected;
    }

    public void merge(CartItem item) {
        this.quantity += item.getQuantity();
        this.selected = this.selected || item.isSelected();
    }

    private void validateQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidCartRequestException("quantity는 1 이상이어야 합니다.");
        }
    }
}
