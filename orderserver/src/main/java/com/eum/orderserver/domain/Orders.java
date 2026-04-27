package com.eum.orderserver.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false)
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "paid_amount")
    private Long paidAmount;

    @Builder.Default
    @OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetails> orderDetails = new ArrayList<>();

    @Column(name = "receiver_name")
    private String receiverName;
    @Column(name = "receiver_phone")
    private String receiverPhone;
    @Column(name = "receiver_addr")
    private String receiverAddr;

    @Builder.Default
    @Column(name = "delete_yn")
    private String deleteYn = "N";

    @Column(name = "time")
    private LocalDateTime time;

    // 주문 상태
    @Column(name = "order_state")
    @Enumerated(EnumType.STRING)
    private OrderState orderState;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @PrePersist
    private void generateOrderId() {
        if (this.orderId == null) {
            this.orderId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        }
    }

    public void addOrderDetail(OrderDetails detail) {
        this.orderDetails.add(detail);
        detail.setOrders(this);
    }
}