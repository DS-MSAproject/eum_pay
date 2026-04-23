package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_terms_agreements")
@Getter
@Setter
@NoArgsConstructor
public class UserTermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // users 테이블 FK
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 약관 ID (service_terms, privacy_policy 등)
    @Column(name = "term_id", nullable = false)
    private String termId;

    // 약관 버전 (변경 추적용)
    @Column(name = "term_version", nullable = false)
    private String termVersion;

    // 동의 여부 (true = 동의, false = 미동의)
    @Column(nullable = false)
    private boolean agreed;

    // 동의한 시각 (법적 증거)
    @CreationTimestamp
    @Column(name = "agreed_at", updatable = false)
    private LocalDateTime agreedAt;

    // 동의자 IP 주소 (법적 증거)
    @Column(name = "ip_address")
    private String ipAddress;

    // 동의자 User-Agent (법적 증거)
    @Column(name = "user_agent")
    private String userAgent;

    // Unique: 한 사용자당 각 약관 1개 레코드만 존재
    @Table(
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"user_id", "term_id"})
            }
    )
    private static final class UniqueConstraintMarker {}

    public static UserTermsAgreement of(Long userId, String termId, String termVersion,
                                        boolean agreed, String ipAddress, String userAgent) {
        UserTermsAgreement agreement = new UserTermsAgreement();
        agreement.setUserId(userId);
        agreement.setTermId(termId);
        agreement.setTermVersion(termVersion);
        agreement.setAgreed(agreed);
        agreement.setIpAddress(ipAddress);
        agreement.setUserAgent(userAgent);
        return agreement;
    }
}
