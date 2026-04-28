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

    // 프로필 PK
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

    // 프로필 최초 생성 시각
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 프로필 마지막 수정 시각
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 회원가입/소셜 로그인 직후 기본 프로필을 생성할 때 사용하는 팩토리 메서드
    public static Profile createDefault(Long userId) {
        Profile profile = new Profile();
        profile.setUserId(userId);
        return profile;
    }
}
