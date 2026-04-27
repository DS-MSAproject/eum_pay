package com.eum.inventoryserver.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 단위 재고 예약 엔티티입니다.
 *
 * orderId를 unique로 두어 같은 주문 이벤트가 다시 들어와도 동일 예약을 재사용합니다.
 * 예약 생성 시점에는 재고 가능 여부만 확인하고, 실제 가용 재고는 결제 완료 시 차감합니다.
 */
@Getter
@Entity
@Table(name = "inventory_reservation", indexes = {
        @Index(name = "idx_inventory_reservation_order_id", columnList = "order_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_reservation_order_id", columnNames = "order_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryReservationStatus status;

    @Column(name = "source_event_id", nullable = false, length = 36)
    private String sourceEventId;

    // 예약 실패 또는 재고 해제 사유를 주문 서버에 전달하기 위한 메시지입니다.
    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<InventoryReservationItem> items = new ArrayList<>();

    private InventoryReservation(Long orderId, String sourceEventId) {
        this.orderId = orderId;
        this.sourceEventId = sourceEventId;
        this.status = InventoryReservationStatus.RESERVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static InventoryReservation reserved(Long orderId, String sourceEventId, List<InventoryReservationItem> items) {
        InventoryReservation reservation = new InventoryReservation(orderId, sourceEventId);
        items.forEach(reservation::addItem);
        return reservation;
    }

    // 실패한 예약도 기록으로 남겨 같은 주문 이벤트가 재처리될 때 같은 실패 결과를 반환합니다.
    public static InventoryReservation rejected(Long orderId, String sourceEventId, String reason) {
        InventoryReservation reservation = new InventoryReservation(orderId, sourceEventId);
        reservation.status = InventoryReservationStatus.REJECTED;
        reservation.reason = reason;
        return reservation;
    }

    public void addItem(InventoryReservationItem item) {
        this.items.add(item);
        item.attach(this);
    }

    public boolean isReserved() {
        return this.status == InventoryReservationStatus.RESERVED;
    }

    public boolean isConfirmed() {
        return this.status == InventoryReservationStatus.CONFIRMED;
    }

    public boolean isRejected() {
        return this.status == InventoryReservationStatus.REJECTED;
    }

    public boolean isReleased() {
        return this.status == InventoryReservationStatus.RELEASED;
    }

    // PaymentCompleted 이후 예약을 최종 판매로 확정합니다.
    // 실제 재고 차감은 InventoryService.confirmReservedStock()에서 수행합니다.
    public void confirm() {
        if (isReleased() || isRejected()) {
            throw new IllegalStateException("해제되었거나 거절된 예약은 확정할 수 없습니다. orderId=" + orderId);
        }
        this.status = InventoryReservationStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    // PaymentFailed 또는 환불 성공 시 예약을 RELEASED 상태로 전환합니다.
    // 이미 RELEASED면 중복 이벤트로 보고 추가 복구 없이 종료합니다.
    public void release(String reason) {
        if (isReleased()) {
            return;
        }
        if (isRejected()) {
            throw new IllegalStateException("거절된 예약은 해제할 수 없습니다. orderId=" + orderId);
        }
        this.status = InventoryReservationStatus.RELEASED;
        this.reason = reason;
        this.updatedAt = LocalDateTime.now();
    }
}
