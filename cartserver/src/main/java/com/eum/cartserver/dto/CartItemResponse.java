package com.eum.cartserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class CartItemResponse {
    private Long productId;
    private Long optionId;
    private Long quantity;

    @JsonProperty("isSelected")
    private boolean selected;

    @JsonProperty("isSoldOut")
    private boolean soldOut;
}
