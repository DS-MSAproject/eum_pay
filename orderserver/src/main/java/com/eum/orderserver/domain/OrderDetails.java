package com.eum.orderserver.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "order_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetails {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) // 1. 전략 명시
    @Column(name = "detail_id")
    private Long id; // 2. 접근 제어자 private 권장

    // 주문자 고유번호
    @Column(name = "user_id")
    private Long userId;
    // 주문 상품 고유번호
    @Column(name = "item_id")
    private Long productId;
    // 주문 상품 옵션 고유번호
    @Column(name = "option_id")
    private Long optionId;
    // 주문 상품 가격
    @Column(name = "price")
    private Long price;
    // 주문 상품 수량
    @Column(name = "amount")
    private Long quantity;
    // 해당 주문 상품 총 가격
    // 개별 가격 * 수량
    @Column(name = "total_price")
    private Long totalPrice;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshotJson = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY) // 지연 로딩 권장
    @JoinColumn(name = "order_id")
    private Orders orders;

    public void refreshSnapshot(Map<String, Object> snapshotJson) {
        this.snapshotJson = normalizeSnapshot(snapshotJson);
    }

    public String getProductName() {
        return stringValue("productName");
    }

    public String getOptionName() {
        return stringValue("optionName");
    }

    public static Map<String, Object> initialSnapshot(Long productId, Long optionId) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("productId", productId);
        snapshot.put("optionId", optionId);
        snapshot.put("productName", "");
        snapshot.put("optionName", null);
        snapshot.put("capturedAt", LocalDateTime.now().toString());
        return snapshot;
    }

    public static Map<String, Object> checkoutSnapshot(Long productId, Long optionId, String productName,
                                                       String optionName, Long unitPrice, Long quantity,
                                                       Long discountPrice, Long extraPrice, Long lineTotalPrice,
                                                       LocalDateTime capturedAt) {
        Map<String, Object> snapshot = initialSnapshot(productId, optionId);
        snapshot.put("productName", productName);
        snapshot.put("optionName", optionName);
        snapshot.put("unitPrice", unitPrice);
        snapshot.put("price", unitPrice);
        snapshot.put("discountPrice", discountPrice);
        snapshot.put("extraPrice", extraPrice);
        snapshot.put("quantity", quantity);
        snapshot.put("lineTotalPrice", lineTotalPrice);
        snapshot.put("totalPrice", lineTotalPrice);
        snapshot.put("capturedAt", capturedAt != null ? capturedAt.toString() : LocalDateTime.now().toString());
        return snapshot;
    }

    private Map<String, Object> normalizeSnapshot(Map<String, Object> snapshotJson) {
        Map<String, Object> normalized = initialSnapshot(this.productId, this.optionId);
        if (snapshotJson != null) {
            normalized.putAll(snapshotJson);
        }
        return normalized;
    }

    private String stringValue(String key) {
        Object value = snapshotJson.get(key);
        return value != null ? String.valueOf(value) : "";
    }
}
