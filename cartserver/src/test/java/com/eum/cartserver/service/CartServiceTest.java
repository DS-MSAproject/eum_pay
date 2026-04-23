package com.eum.cartserver.service;

import com.eum.cartserver.domain.Cart;
import com.eum.cartserver.domain.CartItem;
import com.eum.cartserver.dto.CartResponse;
import com.eum.cartserver.repository.CartItemRepository;
import com.eum.cartserver.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private CartService cartService;

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = new Cart(7L);
        ReflectionTestUtils.setField(cart, "id", 11L);
    }

    @Test
    void addItem_createsCartWithInsertIfAbsentAndUpsertsQuantity() {
        CartItem persistedItem = new CartItem(cart, 100L, 200L, 3L);
        ReflectionTestUtils.setField(persistedItem, "id", 31L);
        setAuditFields(persistedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty(), Optional.of(cart));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(persistedItem));

        CartResponse response = cartService.addItem(7L, 100L, 200L, 3L);

        verify(cartRepository).insertCartIfAbsent(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository).upsertQuantity(eq(11L), eq(100L), eq(200L), eq(3L), eq(true),
                any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(3L);
        assertThat(response.getItems().get(0).isSelected()).isTrue();
    }

    @Test
    void addItem_usesExistingCartWithoutTryingToInsertAnotherCart() {
        CartItem persistedItem = new CartItem(cart, 100L, 200L, 5L);
        ReflectionTestUtils.setField(persistedItem, "id", 32L);
        setAuditFields(persistedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(persistedItem));

        CartResponse response = cartService.addItem(7L, 100L, 200L, 5L);

        verify(cartRepository, never()).insertCartIfAbsent(any(Long.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository).upsertQuantity(eq(11L), eq(100L), eq(200L), eq(5L), eq(true),
                any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void addItem_withoutOption_usesNullableBusinessKeyPath() {
        CartItem persistedItem = new CartItem(cart, 100L, null, 4L);
        ReflectionTestUtils.setField(persistedItem, "id", 35L);
        setAuditFields(persistedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(persistedItem));

        CartResponse response = cartService.addItem(7L, 100L, null, 4L);

        verify(cartItemRepository).upsertQuantityWithoutOption(eq(11L), eq(100L), eq(4L), eq(true),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository, never()).upsertQuantity(eq(11L), eq(100L), any(Long.class), eq(4L), eq(true),
                any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getOptionId()).isNull();
    }

    @Test
    void getCart_returnsEmptyResponseWhenNoCartExists() {
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(7L);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void getCart_returnsExistingItemsWithSelectedState() {
        CartItem firstItem = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(firstItem, "id", 33L);
        setAuditFields(firstItem);

        CartItem secondItem = new CartItem(cart, 101L, 201L, 1L);
        ReflectionTestUtils.setField(secondItem, "id", 34L);
        secondItem.updateSelected(false);
        setAuditFields(secondItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(firstItem, secondItem));

        CartResponse response = cartService.getCart(7L);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).isSelected()).isTrue();
        assertThat(response.getItems().get(1).isSelected()).isFalse();
    }

    @Test
    void updateOption_mergesQuantityIntoTargetOptionUsingUpsert() {
        CartItem sourceItem = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(sourceItem, "id", 41L);
        sourceItem.updateSelected(false);
        setAuditFields(sourceItem);

        CartItem mergedItem = new CartItem(cart, 100L, 201L, 7L);
        ReflectionTestUtils.setField(mergedItem, "id", 42L);
        mergedItem.updateSelected(false);
        setAuditFields(mergedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(sourceItem));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(mergedItem));

        CartResponse response = cartService.updateOption(7L, 100L, 200L, 201L);

        ArgumentCaptor<Long> quantityCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cartItemRepository).upsertQuantity(eq(11L), eq(100L), eq(201L), quantityCaptor.capture(), eq(false),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository).delete(sourceItem);
        assertThat(quantityCaptor.getValue()).isEqualTo(2L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getOptionId()).isEqualTo(201L);
        assertThat(response.getItems().get(0).isSelected()).isFalse();
    }

    @Test
    void updateOption_returnsCurrentCartWhenOptionDoesNotChange() {
        CartItem item = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(item, "id", 43L);
        setAuditFields(item);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(item));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(item));

        CartResponse response = cartService.updateOption(7L, 100L, 200L, 200L);

        verify(cartItemRepository, never()).upsertQuantity(any(Long.class), any(Long.class), any(Long.class),
                any(Long.class), any(Boolean.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository, never()).delete(any(CartItem.class));
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void updateOption_returnsCurrentCartWhenBothOptionsAreNull() {
        CartItem item = new CartItem(cart, 100L, null, 2L);
        ReflectionTestUtils.setField(item, "id", 53L);
        setAuditFields(item);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, null))
                .thenReturn(Optional.of(item));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(item));

        CartResponse response = cartService.updateOption(7L, 100L, null, null);

        verify(cartItemRepository, never()).upsertQuantity(any(Long.class), any(Long.class), any(Long.class),
                any(Long.class), any(Boolean.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository, never()).upsertQuantityWithoutOption(any(Long.class), any(Long.class), any(Long.class),
                any(Boolean.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository, never()).delete(any(CartItem.class));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getOptionId()).isNull();
    }

    @Test
    void updateOption_passesSelectedStateWhenMovingSelectedItem() {
        CartItem sourceItem = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(sourceItem, "id", 51L);
        sourceItem.updateSelected(true);
        setAuditFields(sourceItem);

        CartItem movedItem = new CartItem(cart, 100L, 201L, 2L);
        ReflectionTestUtils.setField(movedItem, "id", 52L);
        movedItem.updateSelected(true);
        setAuditFields(movedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(sourceItem));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(movedItem));

        CartResponse response = cartService.updateOption(7L, 100L, 200L, 201L);

        verify(cartItemRepository).upsertQuantity(eq(11L), eq(100L), eq(201L), eq(2L), eq(true),
                any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).isSelected()).isTrue();
    }

    @Test
    void updateOption_movesItemToNoOptionBucket() {
        CartItem sourceItem = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(sourceItem, "id", 54L);
        sourceItem.updateSelected(false);
        setAuditFields(sourceItem);

        CartItem movedItem = new CartItem(cart, 100L, null, 2L);
        ReflectionTestUtils.setField(movedItem, "id", 55L);
        movedItem.updateSelected(false);
        setAuditFields(movedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(sourceItem));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(movedItem));

        CartResponse response = cartService.updateOption(7L, 100L, 200L, null);

        verify(cartItemRepository).upsertQuantityWithoutOption(eq(11L), eq(100L), eq(2L), eq(false),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository).delete(sourceItem);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getOptionId()).isNull();
    }

    @Test
    void updateOption_movesItemFromNoOptionToSpecificOption() {
        CartItem sourceItem = new CartItem(cart, 100L, null, 2L);
        ReflectionTestUtils.setField(sourceItem, "id", 56L);
        sourceItem.updateSelected(true);
        setAuditFields(sourceItem);

        CartItem movedItem = new CartItem(cart, 100L, 201L, 2L);
        ReflectionTestUtils.setField(movedItem, "id", 57L);
        movedItem.updateSelected(true);
        setAuditFields(movedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, null))
                .thenReturn(Optional.of(sourceItem));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(movedItem));

        CartResponse response = cartService.updateOption(7L, 100L, null, 201L);

        verify(cartItemRepository).upsertQuantity(eq(11L), eq(100L), eq(201L), eq(2L), eq(true),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cartItemRepository).delete(sourceItem);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getOptionId()).isEqualTo(201L);
    }

    @Test
    void updateItem_deletesItemWhenQuantityBecomesZero() {
        CartItem item = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(item, "id", 45L);
        setAuditFields(item);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(item));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of());

        CartResponse response = cartService.updateItem(7L, 100L, 200L, 0L);

        verify(cartItemRepository).delete(item);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void updateItemSelection_returnsResponseWithUpdatedSelectedState() {
        CartItem item = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(item, "id", 44L);
        setAuditFields(item);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(item));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(item));

        CartResponse response = cartService.updateItemSelection(7L, 100L, 200L, false);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).isSelected()).isFalse();
    }

    @Test
    void removeItem_deletesRequestedItem() {
        CartItem item = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(item, "id", 46L);
        setAuditFields(item);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByBusinessKeyWithLock(11L, 100L, 200L))
                .thenReturn(Optional.of(item));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of());

        CartResponse response = cartService.removeItem(7L, 100L, 200L);

        verify(cartItemRepository).delete(item);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void updateAllSelection_returnsEmptyCartWhenNoCartExists() {
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty());

        CartResponse response = cartService.updateAllSelection(7L, false);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getItems()).isEmpty();
        verify(cartItemRepository, never()).findAllByCart_IdOrderByCreatedAtAsc(any(Long.class));
    }

    @Test
    void updateAllSelection_updatesEveryItemSelectionState() {
        CartItem firstItem = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(firstItem, "id", 47L);
        setAuditFields(firstItem);

        CartItem secondItem = new CartItem(cart, 101L, 201L, 1L);
        ReflectionTestUtils.setField(secondItem, "id", 48L);
        setAuditFields(secondItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(firstItem, secondItem));

        CartResponse response = cartService.updateAllSelection(7L, false);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems()).allMatch(item -> !item.isSelected());
    }

    @Test
    void removeSelectedItems_returnsEmptyCartWhenNoCartExists() {
        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.empty());

        CartResponse response = cartService.removeSelectedItems(7L);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getItems()).isEmpty();
        verify(cartItemRepository, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void removeSelectedItems_deletesOnlySelectedItems() {
        CartItem selectedItem = new CartItem(cart, 100L, 200L, 2L);
        ReflectionTestUtils.setField(selectedItem, "id", 49L);
        setAuditFields(selectedItem);

        CartItem unselectedItem = new CartItem(cart, 101L, 201L, 1L);
        ReflectionTestUtils.setField(unselectedItem, "id", 50L);
        unselectedItem.updateSelected(false);
        setAuditFields(unselectedItem);

        when(cartRepository.findByUserId(7L))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(11L))
                .thenReturn(List.of(selectedItem, unselectedItem))
                .thenReturn(List.of(unselectedItem));

        CartResponse response = cartService.removeSelectedItems(7L);

        verify(cartItemRepository).deleteAll(eq(List.of(selectedItem)));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(101L);
    }

    private void setAuditFields(CartItem item) {
        ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(item, "updatedAt", LocalDateTime.now());
    }
}
