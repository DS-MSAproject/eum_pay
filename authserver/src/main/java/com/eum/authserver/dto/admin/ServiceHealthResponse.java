package com.eum.authserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ServiceHealthResponse {

    private String serviceName;
    private String status;           // UP / DOWN / UNKNOWN
    private Double cpuUsage;         // 0~100 %
    private Double memoryUsagePercent; // 0~100 %
    private Long memoryUsedMb;
    private Long memoryMaxMb;
    private Long responseTimeMs;
    private LocalDateTime checkedAt;
}
