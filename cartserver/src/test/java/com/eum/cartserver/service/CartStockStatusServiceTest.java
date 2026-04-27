package com.eum.cartserver.service;

import com.eum.cartserver.client.InventoryStockClient;
import com.eum.cartserver.client.dto.InventoryStockResponse;
import com.eum.cartserver.dto.CartItemResponse;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartStockStatusServiceTest {

    @Mock
    private InventoryStockClient inventoryStockClient;

    @InjectMocks
    private CartStockStatusService cartStockStatusService;

    @Test
    void enrichSoldOutStatus_marksSoldOutByOptionStock() {
        List<CartItemResponse> items = List.of(
                CartItemResponse.builder().productId(100L).optionId(0L).quantity(1L).selected(true).build(),
                CartItemResponse.builder().productId(101L).optionId(201L).quantity(1L).selected(true).build()
        );

        when(inventoryStockClient.getStocks(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        new InventoryStockResponse(100L, null, 3, "AVAILABLE"),
                        new InventoryStockResponse(101L, 201L, 0, "SOLDOUT")
                ));

        List<CartItemResponse> enriched = cartStockStatusService.enrichSoldOutStatus(items);

        assertThat(enriched).hasSize(2);
        assertThat(enriched.get(0).isSoldOut()).isFalse();
        assertThat(enriched.get(1).isSoldOut()).isTrue();
    }

    @Test
    void enrichSoldOutStatus_requestsDistinctProductIdsOnly() {
        List<CartItemResponse> items = List.of(
                CartItemResponse.builder().productId(100L).optionId(0L).quantity(1L).selected(true).build(),
                CartItemResponse.builder().productId(100L).optionId(201L).quantity(1L).selected(true).build()
        );

        when(inventoryStockClient.getStocks(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        cartStockStatusService.enrichSoldOutStatus(items);

        ArgumentCaptor<com.eum.cartserver.client.dto.InventoryStockRequest> captor =
                ArgumentCaptor.forClass(com.eum.cartserver.client.dto.InventoryStockRequest.class);
        verify(inventoryStockClient).getStocks(captor.capture());
        assertThat(captor.getValue().getProductIds()).containsExactly(100L);
    }

    @Test
    void enrichSoldOutStatus_returnsOriginalItemsWhenStockLookupFails() {
        List<CartItemResponse> items = List.of(
                CartItemResponse.builder().productId(100L).optionId(0L).quantity(1L).selected(true).build()
        );

        when(inventoryStockClient.getStocks(org.mockito.ArgumentMatchers.any()))
                .thenThrow(org.mockito.Mockito.mock(FeignException.class));

        List<CartItemResponse> enriched = cartStockStatusService.enrichSoldOutStatus(items);

        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).isSoldOut()).isFalse();
    }
}
