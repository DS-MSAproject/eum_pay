package com.eum.paymentserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "payment_cancels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 결제 취소 시도 이력 엔티티입니다.
 * 취소 사유, 취소 금액, Toss 취소 결과와 실패 원인을 기록합니다.
 */
public class PaymentCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, length = 500)
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CancelReasonType cancelReasonType;

    @Column(nullable = false)
    private Long cancelAmount;

    @Column(nullable = false, length = 30)
    private String cancelStatus;

    @Column(length = 100)
    private String tossCancelId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 1000)
    private String failureMessage;

    @Builder
    private PaymentCancel(
            Payment payment,
            String cancelReason,
            CancelReasonType cancelReasonType,
            Long cancelAmount,
            String cancelStatus,
            String tossCancelId,
            LocalDateTime completedAt,
            String failureCode,
            String failureMessage
    ) {
        this.payment = payment;
        this.cancelReason = cancelReason;
        this.cancelReasonType = cancelReasonType;
        this.cancelAmount = cancelAmount;
        this.cancelStatus = cancelStatus;
        this.tossCancelId = tossCancelId;
        this.completedAt = completedAt;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public static PaymentCancel success(
            Payment payment,
            String cancelReason,
            CancelReasonType cancelReasonType,
            Long cancelAmount,
            String cancelStatus,
            String tossCancelId
    ) {
        return PaymentCancel.builder()
                .payment(payment)
                .cancelReason(cancelReason)
                .cancelReasonType(cancelReasonType)
                .cancelAmount(cancelAmount)
                .cancelStatus(cancelStatus)
                .tossCancelId(tossCancelId)
                .completedAt(LocalDateTime.now())
                .build();
    }

    public static PaymentCancel failure(
            Payment payment,
            String cancelReason,
            CancelReasonType cancelReasonType,
            Long cancelAmount,
            String failureCode,
            String failureMessage
    ) {
        return PaymentCancel.builder()
                .payment(payment)
                .cancelReason(cancelReason)
                .cancelReasonType(cancelReasonType)
                .cancelAmount(cancelAmount)
                .cancelStatus("FAILED")
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    @PrePersist
    void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }
}
