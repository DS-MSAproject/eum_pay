package com.eum.cartserver.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CartItemsDeleteRequest {

    @Valid
    @NotEmpty
    private List<CartItemDeleteRequest> items;
}
