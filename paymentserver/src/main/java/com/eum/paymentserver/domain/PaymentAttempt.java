package com.eum.paymentserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 결제 승인(confirm) 시도 이력 엔티티입니다.
 * Toss 승인 요청/응답 원문과 성공/실패 결과를 감사 로그처럼 남깁니다.
 */
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, length = 40)
    private String requestType;

    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private PaymentAttempt(
            Payment payment,
            String requestType,
            String requestPayload,
            String responsePayload,
            String result
    ) {
        this.payment = payment;
        this.requestType = requestType;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.result = result;
    }

    public static PaymentAttempt of(
            Payment payment,
            String requestType,
            String requestPayload,
            String responsePayload,
            String result
    ) {
        return PaymentAttempt.builder()
                .payment(payment)
                .requestType(requestType)
                .requestPayload(requestPayload)
                .responsePayload(responsePayload)
                .result(result)
                .build();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
