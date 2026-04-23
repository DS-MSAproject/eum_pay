# inventoryserver 코드 리뷰 리포트

> 작성일: 2026-04-23  
> 검토 범위: inventoryserver 전체 Java 소스 (entity, service, outbox, message, controller, config, idempotency)

---

## 요약

| 심각도 | 건수 | 처리 |
|--------|------|------|
| Critical (버그) | 1 | ✅ 수정 완료 |
| Dead Code | 2 | ✅ 제거 완료 |
| 중복 상수 | 1 | ✅ 제거 완료 |
| 잘못된 주석 | 4 | ✅ 수정 완료 |

---

## 1. [Critical — 버그] 결제 취소 시 orderserver 미통보

### 문제

`InventoryOrderEventHandler.handlePaymentCancelStatus()` 가 `PaymentCancelled` 토픽 수신 후 재고 예약을 해제(`releaseReservedStock`)한 뒤, **결과를 `enqueueRestoreResult()`로 발행하고 있었다.**

`enqueueRestoreResult()`는 `ProductRestoreSucceeded` / `ProductRestoreFailed` 토픽으로 Outbox에 저장하는데, **orderserver는 이 두 토픽을 구독하지 않는다.**

orderserver의 실제 구독 토픽:
```
InventoryReserved, InventoryReservationFailed
InventoryDeducted, InventoryDeductionFailed
InventoryReleased, InventoryReleaseFailed   ← 취소 이벤트가 여기로 와야 함
PaymentCompleted, PaymentFailed
```

### 영향

결제 취소 흐름에서:
- inventoryserver: 재고 예약은 정상적으로 `RELEASED` 상태로 전환됨
- orderserver: 재고 해제 사실을 **전혀 통보받지 못함** → 주문 상태가 취소 완료로 전환되지 않는 버그

### 수정 — `InventoryOrderEventHandler.java`

```java
// Before
ProductRestoreResult result = inventoryService.releaseReservedStock(event.getOrderId(), "PAYMENT_CANCELLED");
inventoryOutboxService.enqueueRestoreResult(result);   // ❌ 아무도 구독하지 않는 토픽 발행

// After
ProductRestoreResult result = inventoryService.releaseReservedStock(event.getOrderId(), "PAYMENT_CANCELLED");
inventoryOutboxService.enqueueReleaseResult(InventoryReleaseResult.builder()
        .orderId(result.getOrderId())
        .success(result.isSuccess())
        .reason(result.getReason())
        .build());   // ✅ orderserver가 구독 중인 InventoryReleased / InventoryReleaseFailed 토픽 발행
```

**결제 실패 경로(`handlePaymentFailedTopic`)와 결제 취소 경로(`handlePaymentCancelStatus`)가 동일한 `enqueueReleaseResult()`를 사용하도록 통일.**  
orderserver 입장에서 두 경우 모두 "예약 재고 해제" 동일 의미이므로 같은 토픽이 맞다.

---

## 2. [Dead Code] `InventoryService.rejectOrderReservation()`

### 문제

```java
@Transactional
public ProductReservationResult rejectOrderReservation(OrderCheckedOutEvent event, String reason) { ... }
```

프로젝트 전체 소스에서 **단 한 곳도 호출되지 않는 데드 메서드.**

구 설계에서는 핸들러가 재고 부족 판단 후 명시적으로 이 메서드를 호출하는 흐름이었으나, 현재는 `reserveOrderStock()` 내부에서 비즈니스 실패를 결과 객체로 직접 반환하는 구조로 바뀌어 불필요해졌다.

### 수정

`InventoryService.java`에서 `rejectOrderReservation()` 메서드 전체 삭제.

---

## 3. [Dead Code] `InventoryOutboxService.enqueueRestoreResult()` 및 관련 상수

### 문제

이슈 #1 수정으로 `enqueueRestoreResult()` 호출이 없어졌다. 해당 메서드와 이 메서드에서만 쓰이던 두 상수가 데드 코드가 됨.

```java
private static final String PRODUCT_RESTORE_SUCCEEDED = "ProductRestoreSucceeded";  // ❌ 미사용
private static final String PRODUCT_RESTORE_FAILED    = "ProductRestoreFailed";     // ❌ 미사용

public void enqueueRestoreResult(ProductRestoreResult event) { ... }                // ❌ 미사용
```

### 수정

`InventoryOutboxService.java`에서 세 항목 모두 삭제. `ProductRestoreResult` import도 함께 제거.

---

## 4. [중복 상수] `PAYMENT_REQUESTED_TOPIC` / `PAYMENT_REQUESTED`

### 문제

```java
private static final String PAYMENT_REQUESTED_TOPIC = "PaymentRequested";  // ❌ 중복
private static final String PAYMENT_REQUESTED        = "PaymentRequested";  // ✅ 유지
```

두 상수가 동일한 값을 가지며, `enqueuePaymentRequested()`에서 둘 다 사용되어 혼란을 야기.

```java
saveOrderOutbox(eventId, event.getOrderId(), PAYMENT_REQUESTED, PAYMENT_REQUESTED_TOPIC, payload);
//                                            eventType           topic
```

### 수정

`PAYMENT_REQUESTED_TOPIC` 상수 삭제. 두 인자 모두 `PAYMENT_REQUESTED`로 통일.

```java
saveOrderOutbox(eventId, event.getOrderId(), PAYMENT_REQUESTED, PAYMENT_REQUESTED, payload);
```

---

## 5. [주석] Saga 용어 잔재 — `보상 트랜잭션` 4곳 수정

이 프로젝트는 Saga 패턴을 사용하지 않으며 Outbox Pattern + 이벤트 체이닝 구조이다. Saga 용어인 "보상 트랜잭션"이 주석에 남아 있어 아키텍처와 불일치했다.

| 파일 | 수정 전 | 수정 후 |
|------|---------|---------|
| `InventoryReservationStatus.java` | `결제 실패 또는 환불 **보상 트랜잭션**으로 예약이 해제된 상태` | `결제 실패 또는 결제 취소 이벤트 수신 후 예약이 해제된 상태` |
| `InventoryReservation.java` | `예약 실패 또는 **보상 트랜잭션** 사유를 주문 서버에 전달` | `예약 실패 또는 재고 해제 사유를 주문 서버에 전달` |
| `InventoryReleaseResult.java` | `결제 실패 **보상 트랜잭션** 결과를 orderserver에 알릴 때 쓰는 payload` | `결제 실패 또는 결제 취소로 재고 예약이 해제된 결과를 orderserver에 알릴 때` |
| `PaymentStatusEvent.java` | `결제 실패는 예약 해제 **보상 트랜잭션**으로 이어집니다` | `결제 실패는 InventoryReleased 이벤트 체이닝으로 이어집니다` |

---

## 6. 정상 확인 항목

| 항목 | 판정 |
|------|------|
| `@Transactional` 경계 — 서비스 계층에서 재고 변경과 Outbox INSERT 원자적 처리 | ✅ 정상 |
| 멱등성 — `InventoryProcessedEvent`로 중복 이벤트 무시 | ✅ 정상 |
| 비즈니스 실패 결과 객체 반환 — TX 오염 없이 Kafka 재처리 보장 | ✅ 정상 |
| `reserveOrderStock` 멱등 처리 — 동일 orderId 예약 재처리 시 기존 결과 반환 | ✅ 정상 |
| `tryConfirmReservedStock` — CONFIRMED/RELEASED/REJECTED 상태 검증 | ✅ 정상 |
| `releaseReservedStock` — CONFIRMED 상태에서만 실제 재고 복구 | ✅ 정상 |
| `InventoryOutbox` Debezium 라우팅 — `topic` 컬럼 기준으로 Kafka 토픽 결정 | ✅ 정상 |
| DLQ 설정 — 4개 consumer 모두 `enable-dlq: true` | ✅ 정상 |
| `InventoryProcessedEvent.eventId` unique 제약 — DB 레벨 중복 방지 | ✅ 정상 |
| `InventoryReservation.orderId` unique 제약 — 동일 주문 이중 예약 방지 | ✅ 정상 |

---

## 수정된 파일 목록

| 파일 | 변경 내용 |
|------|---------|
| `service/InventoryOrderEventHandler.java` | `handlePaymentCancelStatus`: `enqueueRestoreResult` → `enqueueReleaseResult` (Critical 버그 수정) |
| `service/InventoryOutboxService.java` | `enqueueRestoreResult()` 삭제, `PRODUCT_RESTORE_SUCCEEDED/FAILED` 상수 삭제, `PAYMENT_REQUESTED_TOPIC` 상수 삭제, `ProductRestoreResult` import 제거 |
| `service/InventoryService.java` | `rejectOrderReservation()` 데드 메서드 삭제 |
| `entity/InventoryReservationStatus.java` | `RELEASED` 주석 수정 (Saga 용어 제거) |
| `entity/InventoryReservation.java` | `reason` 필드 주석 수정 (Saga 용어 제거) |
| `message/inventory/InventoryReleaseResult.java` | Javadoc 수정 (Saga 용어 제거) |
| `message/payment/PaymentStatusEvent.java` | Javadoc 수정 (Saga 용어 제거) |
