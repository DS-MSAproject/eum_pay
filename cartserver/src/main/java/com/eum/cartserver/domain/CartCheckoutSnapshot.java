package com.eum.cartserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_checkout_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartCheckoutSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "items", nullable = false, columnDefinition = "TEXT")
    private String items;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static CartCheckoutSnapshot of(Long orderId, Long userId, String itemsJson) {
        CartCheckoutSnapshot snapshot = new CartCheckoutSnapshot();
        snapshot.orderId = orderId;
        snapshot.userId = userId;
        snapshot.items = itemsJson;
        return snapshot;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
