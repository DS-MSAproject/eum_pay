package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인용 아이디 (영문+숫자, unique)
    // 소셜 가입 유저는 provider+providerId 기반으로 자동 생성
    @Column(unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    // 소셜에서 받아온 실명 or 로컬 가입 시 입력한 이름
    // 나중에 프로필서버로 이전 시 이 컬럼 제거 예정
    @Column
    private String name;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column
    private String password;

    @Column(unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_id")
    private String providerId;

    // 소셜 로그인 시 받아온 프로필 사진 URL
    // 나중에 프로필서버로 이전 시 이 컬럼 제거 예정
    @Column(name = "profile_img_url", length = 500)
    private String profileImgUrl;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}