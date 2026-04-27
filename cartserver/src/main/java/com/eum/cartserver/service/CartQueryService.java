package com.eum.cartserver.service;

import com.eum.cartserver.domain.Cart;
import com.eum.cartserver.dto.CartItemResponse;
import com.eum.cartserver.dto.CartSliceResponse;
import com.eum.cartserver.repository.CartItemRepository;
import com.eum.cartserver.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartQueryService {

    private static final int SLICE_SIZE = 10;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartStockStatusService cartStockStatusService;

    public CartSliceResponse getCartSlice(Long userId, Integer page) {
        int safePage = page == null || page < 0 ? 0 : page;

        return cartRepository.findByUserId(userId)
                .map(cart -> buildCartSliceResponse(cart, safePage))
                .orElseGet(() -> buildEmptyCartSliceResponse(userId, safePage));
    }

    private CartSliceResponse buildCartSliceResponse(Cart cart, int page) {
        Slice<CartItemResponse> itemSlice = cartItemRepository.findByCart_IdOrderByCreatedAtDescIdDesc(
                        cart.getId(),
                        PageRequest.of(page, SLICE_SIZE)
                )
                .map(item -> CartItemResponse.builder()
                        .productId(item.getProductId())
                        .optionId(item.getOptionId())
                        .quantity(item.getQuantity())
                        .selected(item.isSelected())
                        .build());

        List<CartItemResponse> items = cartStockStatusService.enrichSoldOutStatus(itemSlice.getContent());
        long selectedItemCount = cartItemRepository.countByCart_IdAndSelectedTrue(cart.getId());
        boolean hasSelectedItems = selectedItemCount > 0;
        boolean hasAnyItems = cartItemRepository.existsByCart_Id(cart.getId());
        boolean allSelected = hasAnyItems && !cartItemRepository.existsByCart_IdAndSelectedFalse(cart.getId());

        return CartSliceResponse.builder()
                .userId(cart.getUserId())
                .selectedItemCount((int) selectedItemCount)
                .allSelected(allSelected)
                .hasSelectedItems(hasSelectedItems)
                .page(itemSlice.getNumber())
                .size(itemSlice.getSize())
                .hasNext(itemSlice.hasNext())
                .items(items)
                .build();
    }

    private CartSliceResponse buildEmptyCartSliceResponse(Long userId, int page) {
        return CartSliceResponse.builder()
                .userId(userId)
                .selectedItemCount(0)
                .allSelected(false)
                .hasSelectedItems(false)
                .page(page)
                .size(SLICE_SIZE)
                .hasNext(false)
                .items(List.of())
                .build();
    }
}
