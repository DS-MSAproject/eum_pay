package com.eum.orderserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class AdminOrderStatsResponse {
    private long todayOrders;
    private long totalOrders;
    private long failedOrders;
    private Map<String, Long> orderStatusBreakdown;
}
