package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // users 테이블과 1:1 관계
    // FK 없이 user_id만 저장 (MSA 원칙)
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 적립금
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int point = 0;

    // SMS 수신 동의
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean smsAllowed = false;

    // 이메일 수신 동의
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean emailAllowed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Profile createDefault(Long userId) {
        Profile profile = new Profile();
        profile.setUserId(userId);
        return profile;
    }
}