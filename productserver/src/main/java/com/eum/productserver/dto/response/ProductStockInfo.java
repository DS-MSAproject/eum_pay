package com.eum.productserver.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductStockInfo {

    private Long productId;
    private Long optionId;
    private Integer stockQuantity;
    private String stockStatus;
}
