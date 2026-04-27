package com.eum.orderserver.dto.product;

import com.eum.orderserver.dto.OrderItemRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutValidationRequest {

    private List<Item> items;

    public static CheckoutValidationRequest from(List<OrderItemRequest> orderItems) {
        List<Item> items = orderItems.stream()
                .map(item -> new Item(item.getProductId(), item.getOptionId(), item.getQuantity()))
                .toList();
        return new CheckoutValidationRequest(items);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private Long optionId;
        private Long quantity;
    }
}