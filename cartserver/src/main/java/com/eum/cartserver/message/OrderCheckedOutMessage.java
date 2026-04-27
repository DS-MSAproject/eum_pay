package com.eum.cartserver.message;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCheckedOutMessage {

    private String eventId;

    @JsonAlias({"orderId"})
    private Long orderId;

    @JsonAlias({"userId", "user_id"})
    private Long userId;

    private List<Item> items;

    @Getter
    @NoArgsConstructor
    public static class Item {
        private Long productId;
        private Long optionId;
    }
}
