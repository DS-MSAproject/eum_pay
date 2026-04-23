package com.eum.cartserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartItemSelectRequest {

    @NotNull
    @Positive
    private Long productId;

    private Long optionId;

    @JsonProperty("isSelected")
    private Boolean selected;
}
