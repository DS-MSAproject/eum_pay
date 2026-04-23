package com.eum.cartserver.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartResponse {
    private Long userId;
    private List<CartItemResponse> items;
}
