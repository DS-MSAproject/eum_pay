package com.eum.paymentserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 결제 본체 엔티티입니다.
 * 주문별 현재 결제 상태와 Toss 승인/실패/취소 결과를 저장합니다.
 */
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String paymentId;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String correlationId;

    @Column(unique = true)
    private String paymentKey;  // toss 승인후 받은 key

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProvider provider;       // toss

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PaymentMethod method;   // 결제 수단

    @Column(length = 64)
    private String easyPayProvider; // 간편 결제

    @Column(nullable = false)
    private Long amount;        // 결제 금액 검증용

    @Column(nullable = false, length = 16)
    private String currency;    // 통화

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentState status;    // 현재 결제 상태

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;  // 중복 요청 안정성 (같은 결제 요청이 여러번 들어와도 서버가
                                    // 꼬이지 않게 하기 위해 사용
    private LocalDateTime approvedAt;       // 결제 승인된 시간
    private LocalDateTime failedAt;         // 결제 실패한 시간
    private LocalDateTime canceledAt;       // 결제 취소된 시간

    @Column(length = 100)
    private String failureCode; // 결제 실패 원인

    @Column(length = 1000)
    private String failureMessage; // 결제 실패 원인

    @Version
    @Column(nullable = false)
    private Long version = 0L;          // 낙관적 락 — 동시 confirm 요청으로 인한 Lost Update 방지

    @Column(nullable = false)
    private LocalDateTime createdAt;    // 결제가 처음 시작된 시간

    @Column(nullable = false)
    private LocalDateTime updatedAt;    // 결제 상태 갱신

    @Builder
    private Payment(
            String paymentId,
            Long orderId,
            Long userId,
            String correlationId,
            PaymentProvider provider,
            Long amount,
            String currency,
            PaymentState status,
            String idempotencyKey
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.correlationId = correlationId;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.method = PaymentMethod.UNKNOWN;
    }

    public static Payment ready(
            Long orderId,
            Long userId,
            String correlationId,
            Long amount,
            String currency
    ) {
        return Payment.builder()
                .paymentId("pay_" + UUID.randomUUID().toString().replace("-", ""))
                .orderId(orderId)
                .userId(userId)
                .correlationId(correlationId)
                .provider(PaymentProvider.TOSS)
                .amount(amount)
                .currency(currency == null || currency.isBlank() ? "KRW" : currency)
                .status(PaymentState.READY)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    public void refreshPrepareContext(Long amount, String currency, String correlationId) {
        this.amount = amount;
        this.currency = currency;
        if (correlationId != null && !correlationId.isBlank()) {
            this.correlationId = correlationId;
        }
        if (this.status == PaymentState.FAILED || this.status == PaymentState.CANCEL_FAILED) {
            this.status = PaymentState.READY;
            this.failureCode = null;
            this.failureMessage = null;
            this.failedAt = null;
            this.idempotencyKey = UUID.randomUUID().toString(); // 재시도 시 새 키 발급
        }
    }

    public void markApprovalRequested() {
        this.status = PaymentState.APPROVAL_REQUESTED;
    }

    public void approve(String paymentKey, PaymentMethod method, String easyPayProvider) {
        this.paymentKey = paymentKey;
        this.method = method == null ? PaymentMethod.UNKNOWN : method;
        this.easyPayProvider = easyPayProvider;
        this.status = PaymentState.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void fail(String failureCode, String failureMessage) {
        this.status = PaymentState.FAILED;
        this.failedAt = LocalDateTime.now();
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public void markCancelRequested() {
        this.status = PaymentState.CANCEL_REQUESTED;
    }

    public void cancel() {
        this.status = PaymentState.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void cancelFailed(String failureCode, String failureMessage) {
        this.status = PaymentState.CANCEL_FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
