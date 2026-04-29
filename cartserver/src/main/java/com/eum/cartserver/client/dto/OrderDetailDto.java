package com.eum.cartserver.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderDetailDto {

    @JsonAlias({"order_id"})
    private Long orderId;

    @JsonAlias({"user_id"})
    private Long userId;

    private List<Item> items;

    @Getter
    @NoArgsConstructor
    public static class Item {
        @JsonAlias({"product_id"})
        private Long productId;
        @JsonAlias({"option_id"})
        private Long optionId;
        private Long quantity;
    }
}
