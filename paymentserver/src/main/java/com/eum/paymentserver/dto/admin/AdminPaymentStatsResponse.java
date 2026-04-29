package com.eum.paymentserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPaymentStatsResponse {
    private long todayRevenue;
    private long totalRevenue;
    private long reconciliationIssues;
}
