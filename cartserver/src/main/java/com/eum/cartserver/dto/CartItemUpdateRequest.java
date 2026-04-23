package com.eum.cartserver.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartItemUpdateRequest {

    @NotNull
    @Positive
    private Long productId;

    private Long optionId;

    @NotNull
    @Min(0)
    private Long quantity;
}
