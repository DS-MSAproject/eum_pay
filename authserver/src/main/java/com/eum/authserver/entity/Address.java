package com.eum.authserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    // 배송지 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 배송지를 소유한 사용자 ID. authserver users.id 값
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 배송지 별명 (기본값: 미지정)
    @Column(name = "address_name", nullable = false)
    private String addressName = "미지정";

    // 수령인 이름
    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    // 우편번호
    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    // 기본 주소 (카카오 주소 API에서 받은 값)
    @Column(name = "base_address", nullable = false)
    private String baseAddress;

    // 상세 주소
    @Column(name = "detail_address")
    private String detailAddress;

    // 참고용 추가 주소 정보 (예: 법정동, 건물명)
    @Column(name = "extra_address")
    private String extraAddress;

    // 사용자가 선택한 주소 타입 (ROAD / JIBUN)
    @Column(name = "address_type", length = 20)
    private String addressType;

    // 수령인 전화번호
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    // 기본 배송지 여부
    @Column(name = "is_default", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isDefault = false;

    // 배송지 최초 등록 시각
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 배송지 마지막 수정 시각
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
