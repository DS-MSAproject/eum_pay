package com.eum.cartserver.service;

import com.eum.cartserver.domain.Cart;
import com.eum.cartserver.dto.CartItemDeleteRequest;
import com.eum.cartserver.dto.CartItemResponse;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.exception.CartNotFoundException;
import com.eum.cartserver.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final ProductOptionPolicyService productOptionPolicyService;
    private final CartStockStatusService cartStockStatusService;

    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(this::buildCartResponse)
                .orElseGet(() -> buildEmptyCartResponse(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, Long productId, Long optionId, Long quantity) {
        Cart cart = getOrCreateCart(userId);
        Long resolvedOptionId = productOptionPolicyService.resolveOptionIdForAdd(productId, optionId);
        cart.addItem(productId, resolvedOptionId, quantity);
        return buildCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long productId, Long optionId, Long quantity) {
        Cart cart = getCartOrThrow(userId);
        cart.changeQuantity(productId, productOptionPolicyService.normalizeOptionId(optionId), quantity);
        return buildCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateOption(Long userId, Long productId, Long optionId, Long newOptionId) {
        Cart cart = getCartOrThrow(userId);
        Long normalizedCurrentOptionId = productOptionPolicyService.normalizeOptionId(optionId);
        Long resolvedNewOptionId = productOptionPolicyService.resolveOptionIdForChange(
                productId,
                normalizedCurrentOptionId,
                newOptionId
        );
        cart.changeOption(productId, normalizedCurrentOptionId, resolvedNewOptionId);
        return buildCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId, Long optionId) {
        Cart cart = getCartOrThrow(userId);
        cart.removeItem(productId, productOptionPolicyService.normalizeOptionId(optionId));
        return buildCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateAllSelection(Long userId, Boolean isSelectedAll) {
        Cart cart = getCartByUserId(userId).orElse(null);
        if (cart == null) {
            return buildEmptyCartResponse(userId);
        }
        boolean selected = isSelectedAll == null || isSelectedAll;
        cart.changeAllSelection(selected);
        return buildCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItemSelection(Long userId, Long productId, Long optionId, Boolean isSelected) {
        Cart cart = getCartOrThrow(userId);
        boolean selected = isSelected == null || isSelected;
        cart.changeSelection(productId, productOptionPolicyService.normalizeOptionId(optionId), selected);
        return buildCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItems(Long userId, List<CartItemDeleteRequest> requests) {
        Cart cart = getCartByUserId(userId).orElse(null);
        if (cart == null) {
            return buildEmptyCartResponse(userId);
        }
        requests.forEach(request -> cart.removeItem(
                request.getProductId(),
                productOptionPolicyService.normalizeOptionId(request.getOptionId())
        ));
        return buildCartResponse(cartRepository.save(cart));
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

    private CartResponse buildEmptyCartResponse(Long userId) {
        return CartResponse.builder()
                .userId(userId)
                .selectedItemCount(0)
                .allSelected(false)
                .hasSelectedItems(false)
                .items(List.of())
                .build();
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cartStockStatusService.enrichSoldOutStatus(cart.getItems().stream()
                .map(item -> CartItemResponse.builder()
                        .productId(item.getProductId())
                        .optionId(item.getOptionId())
                        .quantity(item.getQuantity())
                        .selected(item.isSelected())
                        .build())
                .toList());

        int selectedItemCount = (int) itemResponses.stream()
                .filter(CartItemResponse::isSelected)
                .count();
        boolean hasSelectedItems = selectedItemCount > 0;
        boolean allSelected = !itemResponses.isEmpty() && selectedItemCount == itemResponses.size();

        return CartResponse.builder()
                .userId(cart.getUserId())
                .selectedItemCount(selectedItemCount)
                .allSelected(allSelected)
                .hasSelectedItems(hasSelectedItems)
                .items(itemResponses)
                .build();
    }
}
