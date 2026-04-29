package com.eum.inventoryserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminInventoryStatsResponse {
    private long lowStockCount;
}
