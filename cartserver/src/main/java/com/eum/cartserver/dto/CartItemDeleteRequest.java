package com.eum.cartserver.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDeleteRequest {

    @NotNull
    @Positive
    private Long productId;

    @PositiveOrZero
    private Long optionId;
}
