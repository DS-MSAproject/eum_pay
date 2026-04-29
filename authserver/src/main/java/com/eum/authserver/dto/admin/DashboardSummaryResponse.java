package com.eum.authserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class DashboardSummaryResponse {

    private long todayOrders;
    private long totalOrders;
    private long failedOrders;
    private long todayRevenue;
    private long totalRevenue;
    private long reconciliationIssues;
    private long lowStockCount;
    private int healthyServices;
    private int totalServices;
    private Map<String, Long> orderStatusBreakdown;
    private Map<String, Long> kafkaLagSummary;
}
