package com.eum.cartserver.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartResponse {
    private Long userId;
    private int selectedItemCount;
    private boolean allSelected;
    private boolean hasSelectedItems;
    private List<CartItemResponse> items;
}
