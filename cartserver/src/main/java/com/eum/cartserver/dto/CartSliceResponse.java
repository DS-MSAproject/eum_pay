package com.eum.cartserver.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartSliceResponse {
    private Long userId;
    private int selectedItemCount;
    private boolean allSelected;
    private boolean hasSelectedItems;
    private int page;
    private int size;
    private boolean hasNext;
    private List<CartItemResponse> items;
}
