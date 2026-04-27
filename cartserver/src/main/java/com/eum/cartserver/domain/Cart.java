package com.eum.cartserver.domain;

import com.eum.cartserver.exception.CartItemNotFoundException;
import com.eum.cartserver.exception.InvalidCartRequestException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cart")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC, id DESC")
    private List<CartItem> items = new ArrayList<>();

    public Cart(Long userId) {
        this.userId = userId;
    }

    public void addItem(Long productId, Long optionId, Long quantity) {
        validateQuantity(quantity, false);
        Long normalizedOptionId = normalizeOptionId(optionId);

        findItem(productId, normalizedOptionId)
                .ifPresentOrElse(
                        item -> item.increaseQuantity(quantity),
                        () -> attachItem(new CartItem(productId, normalizedOptionId, quantity))
                );
    }

    public void changeQuantity(Long productId, Long optionId, Long quantity) {
        validateQuantity(quantity, true);
        CartItem item = getItemOrThrow(productId, optionId);

        if (quantity == 0) {
            detachItem(item);
            return;
        }

        item.changeQuantity(quantity);
    }

    public void changeOption(Long productId, Long optionId, Long newOptionId) {
        CartItem item = getItemOrThrow(productId, optionId);
        Long normalizedNewOptionId = normalizeOptionId(newOptionId);

        if (item.hasBusinessKey(productId, normalizedNewOptionId)) {
            return;
        }

        findItem(productId, normalizedNewOptionId)
                .ifPresentOrElse(
                        existingItem -> {
                            existingItem.merge(item);
                            detachItem(item);
                        },
                        () -> item.changeOption(normalizedNewOptionId)
                );
    }

    public void changeSelection(Long productId, Long optionId, boolean selected) {
        getItemOrThrow(productId, optionId).changeSelected(selected);
    }

    public void changeAllSelection(boolean selected) {
        items.forEach(item -> item.changeSelected(selected));
    }

    public void removeItem(Long productId, Long optionId) {
        detachItem(getItemOrThrow(productId, optionId));
    }

    private CartItem getItemOrThrow(Long productId, Long optionId) {
        Long normalizedOptionId = normalizeOptionId(optionId);
        return findItem(productId, normalizedOptionId)
                .orElseThrow(() -> new CartItemNotFoundException("장바구니에 해당 상품이 없습니다."));
    }

    private java.util.Optional<CartItem> findItem(Long productId, Long optionId) {
        return items.stream()
                .filter(item -> item.hasBusinessKey(productId, optionId))
                .findFirst();
    }

    private void attachItem(CartItem item) {
        item.attachTo(this);
        items.add(item);
    }

    private void detachItem(CartItem item) {
        items.remove(item);
        item.detachFromCart();
    }

    private void validateQuantity(Long quantity, boolean allowZero) {
        if (quantity == null) {
            throw new InvalidCartRequestException("quantity는 필수입니다.");
        }

        long min = allowZero ? 0L : 1L;
        if (quantity < min) {
            throw new InvalidCartRequestException("quantity는 " + min + " 이상이어야 합니다.");
        }
    }

    private Long normalizeOptionId(Long optionId) {
        long normalizedOptionId = optionId == null ? 0L : optionId;
        if (normalizedOptionId < 0L) {
            throw new InvalidCartRequestException("optionId는 0 이상이어야 합니다.");
        }
        return normalizedOptionId;
    }
}
