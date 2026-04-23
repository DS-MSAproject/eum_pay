package com.eum.cartserver.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartItemAddRequest {

    @NotNull
    @Positive
    private Long productId;

    private Long optionId;

    @NotNull
    @Min(1)
    private Long quantity;
}
