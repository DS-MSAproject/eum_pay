package com.eum.cartserver.service;

import com.eum.cartserver.domain.Cart;
import com.eum.cartserver.domain.CartItem;
import com.eum.cartserver.dto.CartItemResponse;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.exception.CartItemNotFoundException;
import com.eum.cartserver.exception.CartNotFoundException;
import com.eum.cartserver.repository.CartItemRepository;
import com.eum.cartserver.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::buildCartResponse)
                .orElseGet(() -> buildEmptyCartResponse(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, Long productId, Long optionId, Long quantity) {
        Cart cart = getOrCreateCart(userId);
        LocalDateTime now = LocalDateTime.now();
        upsertQuantity(cart.getId(), productId, optionId, quantity, true, now);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long productId, Long optionId, Long quantity) {
        Cart cart = getCartOrThrow(userId);
        CartItem item = findItemOrThrow(cart, productId, optionId);

        if (quantity == 0) {
            cartItemRepository.delete(item);
            return buildCartResponse(cart);
        }

        item.updateQuantity(quantity);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateOption(Long userId, Long productId, Long optionId, Long newOptionId) {
        Cart cart = getCartOrThrow(userId);
        CartItem item = findItemOrThrow(cart, productId, optionId);
        if (Objects.equals(item.getOptionId(), newOptionId)) {
            return buildCartResponse(cart);
        }

        LocalDateTime now = LocalDateTime.now();
        upsertQuantity(cart.getId(), productId, newOptionId, item.getQuantity(), item.isSelected(), now);
        cartItemRepository.delete(item);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId, Long optionId) {
        Cart cart = getCartOrThrow(userId);
        CartItem item = findItemOrThrow(cart, productId, optionId);
        cartItemRepository.delete(item);
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateAllSelection(Long userId, Boolean isSelectedAll) {
        Cart cart = getCartByUserId(userId).orElse(null);
        if (cart == null) {
            return buildEmptyCartResponse(userId);
        }
        boolean selected = isSelectedAll == null || isSelectedAll;

        List<CartItem> items = cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(cart.getId());
        items.forEach(item -> item.updateSelected(selected));
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItemSelection(Long userId, Long productId, Long optionId, Boolean isSelected) {
        Cart cart = getCartOrThrow(userId);
        CartItem item = findItemOrThrow(cart, productId, optionId);
        boolean selected = isSelected == null || isSelected;
        item.updateSelected(selected);
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse removeSelectedItems(Long userId) {
        Cart cart = getCartByUserId(userId).orElse(null);
        if (cart == null) {
            return buildEmptyCartResponse(userId);
        }
        List<CartItem> selectedItems = cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(cart.getId()).stream()
                .filter(CartItem::isSelected)
                .toList();

        cartItemRepository.deleteAll(selectedItems);
        return buildCartResponse(cart);
    }

    private Cart getOrCreateCart(Long userId) {
        return getCartByUserId(userId)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    cartRepository.insertCartIfAbsent(userId, now, now);
                    return getCartByUserId(userId)
                            .orElseThrow(() -> new IllegalStateException("장바구니 생성에 실패했습니다."));
                });
    }

    private Cart getCartOrThrow(Long userId) {
        return getCartByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("장바구니가 없습니다."));
    }

    private Optional<Cart> getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId);
    }

    private CartItem findItemOrThrow(Cart cart, Long productId, Long optionId) {
        return cartItemRepository.findByBusinessKeyWithLock(cart.getId(), productId, optionId)
                .orElseThrow(() -> new CartItemNotFoundException("장바구니에 해당 상품이 없습니다."));
    }

    private void upsertQuantity(Long cartId, Long productId, Long optionId, Long quantity, boolean selected, LocalDateTime now) {
        if (optionId == null) {
            cartItemRepository.upsertQuantityWithoutOption(cartId, productId, quantity, selected, now, now);
            return;
        }

        cartItemRepository.upsertQuantity(cartId, productId, optionId, quantity, selected, now, now);
    }

    private CartResponse buildEmptyCartResponse(Long userId) {
        return CartResponse.builder()
                .userId(userId)
                .items(List.of())
                .build();
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(cart.getId());

        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> CartItemResponse.builder()
                        .productId(item.getProductId())
                        .optionId(item.getOptionId())
                        .quantity(item.getQuantity())
                        .selected(item.isSelected())
                        .build())
                .toList();

        return CartResponse.builder()
                .userId(cart.getUserId())
                .items(itemResponses)
                .build();
    }
}
