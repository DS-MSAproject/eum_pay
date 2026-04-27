# Kafka 파티션 키 정합성 분석 및 수정 리포트

> 작성일: 2026-04-23  
> 검토 범위: 결제 시스템 전체 Kafka 파티션 키 설정 (Debezium Outbox 커넥터 3개 + Spring Cloud Stream 소비자)

---

## 1. 파티션 키 동작 원리 (현 아키텍처)

이 프로젝트의 Kafka 발행은 **서비스가 직접 Kafka에 쓰지 않고**, 모두 **Outbox 테이블 → Debezium CDC → Kafka** 경로를 거친다.

파티션 키는 Debezium `EventRouter` transform의 `table.field.event.key` 설정으로 결정된다.

```
outbox INSERT
    → Debezium EventRouter
        → message.key  = table.field.event.key 컬럼 값   ← 파티션 키
        → message.value = payload 컬럼 JSON
        → topic         = route.by.field 컬럼 값
```

---

## 2. 수정 전 파티션 키 현황

| Outbox 테이블 | Debezium 커넥터 | `table.field.event.key` | `aggregate_id` 실제 값 | Kafka 파티션 키 |
|-------------|----------------|------------------------|----------------------|----------------|
| `order_outbox` | `dseum-order-outbox-connector` | `aggregate_id` | `orderId` (Long) | `orderId` ✅ |
| `inventory_outbox` | `dseum-inventory-outbox-connector` | `aggregate_id` | `orderId` (Long) | `orderId` ✅ |
| `outbox_events` (payment) | `dseum-payment-outbox-connector` | `aggregate_id` | `paymentId` (String UUID) | `paymentId` ⚠️ |

**결제 관련 이벤트 흐름의 파티션 키**

```
[orderId 기준 파티셔닝]                    [paymentId 기준 파티셔닝]
OrderCheckedOut        → partition X       PaymentCompleted  → partition ?
InventoryReserved      → partition X       PaymentFailed     → partition ?
PaymentRequested       → partition X       PaymentCancelled  → partition ?
InventoryDeducted      → partition X
InventoryReleased      → partition X
```

같은 주문(orderId)에 대한 이벤트임에도 불구하고 **payment outbox만 다른 기준(paymentId)으로 파티셔닝**되는 불일치가 존재했다.

---

## 3. 문제점 상세

### 3-1. 파티션 키 불일치

`paymentId`는 `Payment` 엔티티의 내부 식별자(String, Toss paymentId 또는 자체 생성 UUID)다.  
`orderId`와 1:1 관계이긴 하나, **해시 결과가 달라 파티션 배치가 다를 수 있다.**

예: 3파티션 토픽 기준
```
orderId=100   → hash → partition 0   (OrderCheckedOut, InventoryReserved, ...)
paymentId="abc-xyz-..." → hash → partition 2   (PaymentCompleted, ...)
```

→ 같은 주문의 이벤트들이 서로 다른 파티션에 분산됨

### 3-2. 수평 확장 시 순서 보장 불가

현재는 각 서비스가 단일 인스턴스로 동작하므로 소비자 파티션 배치가 문제되지 않는다.  
그러나 `instance-count > 1`로 수평 확장 시, 같은 주문의 이벤트가 서로 다른 소비자 인스턴스로 분산될 수 있다.

- `InventoryReleased` (orderId 기준, partition 0) → 소비자 인스턴스 A
- `PaymentCancelled` (paymentId 기준, partition 2) → 소비자 인스턴스 B

이 경우 두 이벤트를 처리하는 순서와 상태 일관성을 보장하기 어렵다.

### 3-3. payload `aggregateId` 의미 혼란

`basePayload()`에서 `aggregateId` 필드를 `paymentId`로 설정하고 있었다.

```java
payload.put("aggregateId", payment.getPaymentId());  // 내부 결제 ID
payload.put("orderId", payment.getOrderId());         // 주문 ID
```

이벤트 수신 측(orderserver, inventoryserver)이 `aggregateId`를 읽을 때 `orderId`를 기대하지만 실제로는 `paymentId`가 들어있어 혼란을 야기한다.

---

## 4. 수정 내용

### 수정 파일: `paymentserver/src/main/java/com/eum/paymentserver/Service/PaymentOutboxService.java`

**변경 1: `aggregate_id` (Kafka 파티션 키) = `orderId`로 변경**

```java
// Before
PaymentOutboxEvent.pending(payment.getPaymentId(), eventType, writeJson(payload))
//                          ↑ paymentId → aggregate_id → Kafka 키

// After
PaymentOutboxEvent.pending(String.valueOf(payment.getOrderId()), eventType, writeJson(payload))
//                          ↑ orderId  → aggregate_id → Kafka 키
```

**변경 2: payload의 `aggregateId` 필드도 `orderId`로 통일**

```java
// Before
payload.put("aggregateId", payment.getPaymentId());  // 결제 내부 ID

// After
payload.put("aggregateId", payment.getOrderId());    // 주문 ID (order/inventory outbox와 동일 기준)
payload.put("paymentId", payment.getPaymentId());    // 결제 ID는 별도 필드로 명시 (기존 유지)
```

> `paymentId` 필드는 그대로 payload에 남아있으므로 소비자의 기존 코드 변경 없음.

---

## 5. 수정 후 파티션 키 현황

| Outbox 테이블 | Kafka 파티션 키 | 정합성 |
|-------------|----------------|--------|
| `order_outbox` | `orderId` | ✅ |
| `inventory_outbox` | `orderId` | ✅ |
| `outbox_events` (payment) | `orderId` | ✅ **수정됨** |

**수정 후 결제 흐름 전체가 `orderId` 기준으로 파티셔닝 통일:**

```
[모두 orderId 기준 파티셔닝 → 동일 파티션 보장]

OrderCheckedOut     → partition X
InventoryReserved   → partition X
PaymentRequested    → partition X
PaymentCompleted    → partition X   ← 수정됨
PaymentFailed       → partition X   ← 수정됨
PaymentCancelled    → partition X   ← 수정됨
InventoryDeducted   → partition X
InventoryReleased   → partition X
```

---

## 6. Debezium 커넥터 변경 불필요

`register-connector.sh`의 Debezium 커넥터 설정은 **수정하지 않는다.**

```json
"transforms.outbox.table.field.event.key": "aggregate_id"
```

커넥터는 `aggregate_id` 컬럼 값을 그대로 Kafka 키로 사용하므로,  
**Java 코드에서 `aggregate_id`에 넣는 값을 바꾸는 것만으로 파티션 키가 변경된다.**

---

## 7. 소비자 측 파티션 설정 현황 및 권고

현재 소비자(orderserver, inventoryserver, paymentserver) 바인딩에는 파티션 관련 설정이 없다.

```yaml
# 현재 — 파티션 설정 없음
bindings:
  paymentCompletedConsumer-in-0:
    destination: PaymentCompleted
    group: dseum-order-payment-completed
```

단일 인스턴스 배포에서는 문제없다.  
수평 확장(multi-instance) 배포 시 아래 설정을 추가해야 `orderId` 기준 파티션이 동일 소비자 인스턴스로 라우팅된다:

```yaml
# 수평 확장 시 추가 필요
bindings:
  paymentCompletedConsumer-in-0:
    consumer:
      partitioned: true
      instance-count: 2   # 인스턴스 수
      instance-index: 0   # 각 인스턴스마다 다르게
```

현재 프로젝트 규모에서는 단일 인스턴스 운영이 기준이므로 **지금 당장 적용하지 않고 수평 확장 시점에 추가**하는 것을 권고한다.

---

## 8. 수정 파일 목록

| 파일 | 변경 내용 |
|------|---------|
| `paymentserver/src/main/java/com/eum/paymentserver/Service/PaymentOutboxService.java` | `aggregate_id` → `orderId` (Kafka 파티션 키), `payload.aggregateId` → `orderId` |
