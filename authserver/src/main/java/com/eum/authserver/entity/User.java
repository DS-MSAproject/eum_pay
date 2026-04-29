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

    // 사용자 PK. authserver 내부 사용자 식별자이며 JWT의 userId 기준값으로 사용
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인용 아이디 (영문+숫자, unique)
    // 소셜 가입 유저는 provider+providerId 기반으로 자동 생성
    @Column(unique = true)
    private String username;

    // 로그인/회원 식별에 사용하는 이메일. 로컬/소셜 계정 모두 unique
    @Column(nullable = false, unique = true)
    private String email;

    // 소셜에서 받아온 실명 or 로컬 가입 시 입력한 이름
    // 나중에 프로필서버로 이전 시 이 컬럼 제거 예정
    @Column
    private String name;

    // 권한 정보. 현재는 일반 사용자 ROLE_USER만 사용
    @Enumerated(EnumType.STRING)
    private Role role;

    // 로컬 로그인 비밀번호 해시. 소셜 전용 계정은 비어 있을 수 있음
    @Column
    private String password;

    // 사용자 휴대폰 번호. 회원정보 수정 및 배송지 기본 연락처에 사용
    @Column(unique = true, length = 15)
    private String phoneNumber;

    // 가입/로그인 제공자. local, google, naver, kakao 등
    @Column(name = "provider")
    private String provider;

    // 소셜 제공자가 내려준 사용자 고유 ID. local 계정은 비어 있을 수 있음
    @Column(name = "provider_id")
    private String providerId;

    // 소셜 로그인 시 받아온 프로필 사진 URL
    // 나중에 프로필서버로 이전 시 이 컬럼 제거 예정
    @Column(name = "profile_img_url", length = 500)
    private String profileImgUrl;

    // 이메일 인증 완료 여부. false면 인증 전 상태
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // 회원 최초 생성 시각
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 회원 정보 마지막 수정 시각
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
