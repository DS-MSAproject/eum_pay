package com.eum.authserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ServiceHealthResponse {

    private String serviceName;
    private String status;      // UP / DOWN / UNKNOWN
    private LocalDateTime checkedAt;
}
