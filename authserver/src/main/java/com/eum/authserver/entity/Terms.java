package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Terms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 약관 고유 ID (service_terms, privacy_policy, marketing_sms 등)
    @Column(nullable = false, unique = true)
    private String termId;

    // 약관 제목
    @Column(nullable = false)
    private String title;

    // 약관 본문 (HTML 또는 plain text)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 필수 약관 여부
    @Default
    @Column(nullable = false)
    private boolean isRequired = false;

    // 약관 버전 (2.0, 1.5 등)
    @Default
    @Column(nullable = false)
    private String version = "1.0";

    // 활성 여부 (미사용 약관은 false)
    @Default
    @Column(nullable = false)
    private boolean active = true;

    // 효력 발생일
    @Column(name = "effective_date")
    private LocalDateTime effectiveDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 팩토리 메서드 (기존 코드 호환성)
     */
    public static Terms of(String termId, String title, String content,
                           boolean isRequired, String version) {
        return Terms.builder()
                .termId(termId)
                .title(title)
                .content(content)
                .isRequired(isRequired)
                .version(version)
                .active(true)
                .effectiveDate(LocalDateTime.now())
                .build();
    }
}