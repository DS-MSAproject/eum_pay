package com.eum.orderserver.dto.product;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CheckoutValidationResponse {

    private List<Item> items;
    private Long totalPrice;
    private LocalDateTime capturedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @JsonAlias("product_id")
        private Long productId;

        @JsonAlias("option_id")
        private Long optionId;

        @JsonAlias("product_name")
        private String productName;

        @JsonAlias("option_name")
        private String optionName;

        private Long price;
        private Long discountPrice;
        private Long extraPrice;
        private Long quantity;
        private Long lineTotalPrice;
        private LocalDateTime capturedAt;

        public Long totalPrice() {
            if (lineTotalPrice != null) {
                return lineTotalPrice;
            }
            return safe(price) * safe(quantity);
        }

        private long safe(Long value) {
            return value != null ? value : 0L;
        }
    }
}