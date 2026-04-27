package com.eum.cartserver.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockResponse {

    private Long productId;
    private Long optionId;
    private Integer stockQuantity;
    private String stockStatus;
}
