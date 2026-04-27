package com.eum.orderserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {

    @JsonProperty("productId")
    @NotNull
    @Positive
    private Long productId;

    @JsonProperty("optionId")
    @NotNull
    @PositiveOrZero
    private Long optionId;

    @JsonProperty("quantity")
    @NotNull
    @Positive
    private Long quantity;
}