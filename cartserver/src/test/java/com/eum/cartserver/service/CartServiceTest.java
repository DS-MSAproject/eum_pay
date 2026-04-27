package com.eum.cartserver.service;

import com.eum.cartserver.domain.Cart;
import com.eum.cartserver.dto.CartItemDeleteRequest;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductOptionPolicyService productOptionPolicyService;

    @Mock
    private CartStockStatusService cartStockStatusService;

    @InjectMocks
    private CartService cartService;

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = new Cart(7L);
        ReflectionTestUtils.setField(cart, "id", 11L);
        lenient().when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cartStockStatusService.enrichSoldOutStatus(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void addItem_createsCartWithInsertIfAbsentAndAddsCanonicalOptionItem() {
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty(), Optional.of(cart));
        when(productOptionPolicyService.resolveOptionIdForAdd(100L, null))
                .thenReturn(0L);

        CartResponse response = cartService.addItem(7L, 100L, null, 3L);

        verify(cartRepository).insertCartIfAbsent(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getSelectedItemCount()).isEqualTo(1);
        assertThat(response.isAllSelected()).isTrue();
        assertThat(response.isHasSelectedItems()).isTrue();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(100L);
        assertThat(response.getItems().get(0).getOptionId()).isEqualTo(0L);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(3L);
    }

    @Test
    void addItem_mergesQuantityWhenSameBusinessKeyAlreadyExists() {
        cart.addItem(100L, 200L, 2L);
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(productOptionPolicyService.resolveOptionIdForAdd(100L, 200L))
                .thenReturn(200L);

        CartResponse response = cartService.addItem(7L, 100L, 200L, 3L);

        verify(cartRepository, never()).insertCartIfAbsent(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(5L);
    }

    @Test
    void getCart_returnsEmptyResponseWhenNoCartExists() {
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(7L);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getSelectedItemCount()).isZero();
        assertThat(response.isAllSelected()).isFalse();
        assertThat(response.isHasSelectedItems()).isFalse();
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void updateOption_mergesQuantityIntoTargetOption() {
        cart.addItem(100L, 200L, 2L);
        cart.addItem(100L, 201L, 4L);
        cart.changeSelection(100L, 200L, false);
        cart.changeSelection(100L, 201L, false);
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(productOptionPolicyService.normalizeOptionId(200L))
                .thenReturn(200L);
        when(productOptionPolicyService.resolveOptionIdForChange(100L, 200L, 201L))
                .thenReturn(201L);

        CartResponse response = cartService.updateOption(7L, 100L, 200L, 201L);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getSelectedItemCount()).isZero();
        assertThat(response.isAllSelected()).isFalse();
        assertThat(response.isHasSelectedItems()).isFalse();
        assertThat(response.getItems().get(0).getOptionId()).isEqualTo(201L);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(6L);
        assertThat(response.getItems().get(0).isSelected()).isFalse();
    }

    @Test
    void updateItem_deletesItemWhenQuantityBecomesZero() {
        cart.addItem(100L, 200L, 2L);
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(productOptionPolicyService.normalizeOptionId(200L))
                .thenReturn(200L);

        CartResponse response = cartService.updateItem(7L, 100L, 200L, 0L);

        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void updateItemSelection_returnsResponseWithUpdatedSelectedState() {
        cart.addItem(100L, 200L, 2L);
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(productOptionPolicyService.normalizeOptionId(200L))
                .thenReturn(200L);

        CartResponse response = cartService.updateItemSelection(7L, 100L, 200L, false);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getSelectedItemCount()).isZero();
        assertThat(response.isAllSelected()).isFalse();
        assertThat(response.isHasSelectedItems()).isFalse();
        assertThat(response.getItems().get(0).isSelected()).isFalse();
    }

    @Test
    void updateAllSelection_returnsEmptyCartWhenNoCartExists() {
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty());

        CartResponse response = cartService.updateAllSelection(7L, false);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getSelectedItemCount()).isZero();
        assertThat(response.isAllSelected()).isFalse();
        assertThat(response.isHasSelectedItems()).isFalse();
        assertThat(response.getItems()).isEmpty();
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void removeItems_deletesRequestedItemsOnly() {
        cart.addItem(100L, 200L, 2L);
        cart.addItem(101L, 201L, 1L);
        cart.addItem(102L, 0L, 1L);
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(productOptionPolicyService.normalizeOptionId(200L)).thenReturn(200L);
        when(productOptionPolicyService.normalizeOptionId(0L)).thenReturn(0L);

        CartResponse response = cartService.removeItems(7L, List.of(
                deleteRequest(100L, 200L),
                deleteRequest(102L, 0L)
        ));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getSelectedItemCount()).isEqualTo(1);
        assertThat(response.isAllSelected()).isTrue();
        assertThat(response.isHasSelectedItems()).isTrue();
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(101L);
        assertThat(response.getItems().get(0).isSelected()).isTrue();
    }

    @Test
    void updateAllSelection_setsAggregateFieldsFromItemSelectionState() {
        cart.addItem(100L, 200L, 2L);
        cart.addItem(101L, 201L, 1L);
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));

        CartResponse response = cartService.updateAllSelection(7L, true);

        assertThat(response.getSelectedItemCount()).isEqualTo(2);
        assertThat(response.isAllSelected()).isTrue();
        assertThat(response.isHasSelectedItems()).isTrue();
    }

    private CartItemDeleteRequest deleteRequest(Long productId, Long optionId) {
        CartItemDeleteRequest request = new CartItemDeleteRequest();
        ReflectionTestUtils.setField(request, "productId", productId);
        ReflectionTestUtils.setField(request, "optionId", optionId);
        return request;
    }
}
