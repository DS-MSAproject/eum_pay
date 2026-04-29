package com.eum.inventoryserver.message.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductCreatedEvent {
    private Long productId;
    private List<OptionInfo> options;
    private int initialStock;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionInfo {
        private Long optionId;
        private String optionName;
    }
}
