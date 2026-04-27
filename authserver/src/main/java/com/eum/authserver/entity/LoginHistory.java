package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_histories")
@Getter
@Setter
@NoArgsConstructor
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String clientIp;   // 접속 IP

    @Column(nullable = false)
    private String userAgent;  // 브라우저/기기 정보

    @Column(nullable = false)
    private String provider;   // 로그인 방식 (local, google, naver)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime loginAt;
}