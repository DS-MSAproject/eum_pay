package com.eum.authserver.dto.admin;

import com.eum.authserver.entity.AdminAuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminAuditLogResponse {

    private Long id;
    private String adminEmail;
    private String httpMethod;
    private String requestUri;
    private String result;
    private LocalDateTime createdAt;

    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return AdminAuditLogResponse.builder()
                .id(log.getId())
                .adminEmail(log.getAdminEmail())
                .httpMethod(log.getHttpMethod())
                .requestUri(log.getRequestUri())
                .result(log.getResult())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
