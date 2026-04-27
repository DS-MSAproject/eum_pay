package com.eum.cartserver.service;

import com.eum.cartserver.domain.Cart;
import com.eum.cartserver.domain.CartItem;
import com.eum.cartserver.dto.CartSliceResponse;
import com.eum.cartserver.repository.CartItemRepository;
import com.eum.cartserver.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartQueryServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartStockStatusService cartStockStatusService;

    @InjectMocks
    private CartQueryService cartQueryService;

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = new Cart(7L);
        ReflectionTestUtils.setField(cart, "id", 11L);
    }

    @Test
    void getCartSlice_returnsSliceResponseForLoadMore() {
        List<CartItem> firstSliceItems = List.of(
                item(100L, 0L, 1L),
                item(101L, 0L, 1L)
        );

        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_IdOrderByCreatedAtDescIdDesc(11L, PageRequest.of(0, 10)))
                .thenReturn(new SliceImpl<>(firstSliceItems, PageRequest.of(0, 10), true));
        when(cartStockStatusService.enrichSoldOutStatus(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cartItemRepository.countByCart_IdAndSelectedTrue(11L)).thenReturn(1L);
        when(cartItemRepository.existsByCart_Id(11L)).thenReturn(true);
        when(cartItemRepository.existsByCart_IdAndSelectedFalse(11L)).thenReturn(true);

        CartSliceResponse response = cartQueryService.getCartSlice(7L, 0);

        verify(cartItemRepository).findByCart_IdOrderByCreatedAtDescIdDesc(11L, PageRequest.of(0, 10));
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getSelectedItemCount()).isEqualTo(1);
        assertThat(response.isAllSelected()).isFalse();
        assertThat(response.isHasSelectedItems()).isTrue();
    }

    @Test
    void getCartSlice_returnsEmptySliceWhenCartDoesNotExist() {
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.empty());

        CartSliceResponse response = cartQueryService.getCartSlice(7L, 0);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getSelectedItemCount()).isZero();
    }

    private CartItem item(Long productId, Long optionId, Long quantity) {
        CartItem item = new CartItem(productId, optionId, quantity);
        item.attachTo(cart);
        ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(item, "updatedAt", LocalDateTime.now());
        return item;
    }
}
