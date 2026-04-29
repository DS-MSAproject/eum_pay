package com.eum.orderserver.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ProductSalesResponse {
    private Long productId;
    private String productName;
    private Long totalQuantity;
    private Long totalRevenue;
}
