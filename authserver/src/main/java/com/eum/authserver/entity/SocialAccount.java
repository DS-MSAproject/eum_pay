package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                // 같은 소셜 제공자의 같은 providerId는 하나의 계정에만 저장 가능
                @UniqueConstraint(columnNames = {"provider", "provider_id"}),
                // 한 유저당 같은 소셜 제공자 정보는 하나만 저장
                @UniqueConstraint(columnNames = {"user_id", "provider"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SocialAccount {

    // 소셜 계정 연동 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소셜 계정이 연결된 사용자. 한 사용자는 여러 제공자를 연결할 수 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String provider;    // google, naver, kakao

    @Column(name = "provider_id", nullable = false)
    private String providerId;  // 소셜 제공자 측 고유 ID

    // 소셜 계정 최초 연결 시각
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime connectedAt;

    // 소셜 계정 엔티티 생성용 팩토리 메서드
    public static SocialAccount of(User user, String provider, String providerId) {
        SocialAccount sa = new SocialAccount();
        sa.setUser(user);
        sa.setProvider(provider);
        sa.setProviderId(providerId);
        return sa;
    }
}
