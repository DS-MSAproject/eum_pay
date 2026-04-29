package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_admin_audit_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_email", length = 200)
    private String adminEmail;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AdminAuditLog of(String adminEmail, String httpMethod,
                                   String requestUri, String result) {
        return AdminAuditLog.builder()
                .adminEmail(adminEmail)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .result(result)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
