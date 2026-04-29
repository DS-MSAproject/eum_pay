package com.eum.authserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardSummaryResponse {

    private long totalUsers;
    private long todayNewUsers;
    private long adminActionsToday;
    private int servicesUp;
    private int servicesTotal;
    private List<ServiceHealthResponse> serviceHealthList;
}
