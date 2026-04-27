package com.eum.cartserver.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartItemOptionUpdateRequest {

    @NotNull
    @Positive
    private Long productId;

    @PositiveOrZero
    private Long optionId;

    @NotNull
    @PositiveOrZero
    private Long newOptionId;
}
