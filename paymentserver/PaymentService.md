# PaymentService 이벤트 체이닝 구조

## 개요

orderserver → inventoryserver → paymentserver로 이어지는 결제 Saga 흐름에서
`PaymentOutboxService`가 중간 허브 역할을 담당한다.

---

## 전체 이벤트 체이닝 흐름

```
[orderserver]
  OrderOutbox → OrderCheckedOut 발행
        │
        ▼ (Debezium → Kafka)
[inventoryserver]
  orderCheckedOutConsumer 수신
  → reserveOrderStock()
  ┌─ 성공 → InventoryOutbox: InventoryReserved + PaymentRequested 발행
  └─ 실패 → InventoryOutbox: InventoryReservationFailed 발행
        │
        ▼ PaymentRequested (Debezium → Kafka)
[paymentserver]
  paymentRequestedConsumer 수신
  → prepareRequestedPayment() → Payment 레코드 생성 (READY 상태)
        │
        ▼ (사용자가 결제 confirm 요청)
  confirm() → Toss API 호출
  ┌─ 성공 → PaymentOutboxService.enqueueCompleted() → PaymentCompleted 발행
  └─ 실패 → PaymentOutboxService.enqueueFailed()    → PaymentFailed 발행
        │
        ▼ (Debezium → Kafka)
[inventoryserver]
  paymentCompletedConsumer → tryConfirmReservedStock() → InventoryDeducted / InventoryDeductionFailed 발행
  paymentFailedConsumer    → releaseReservedStock()    → InventoryReleased / InventoryReleaseFailed 발행
        │
        ▼ (Debezium → Kafka)
[orderserver]
  processPaymentCompleted() → ORDER_COMPLETED 상태 전이
  processPaymentFailed()    → PAYMENT_FAILED 상태 전이
```

---

## PaymentOutboxService 발행 토픽 및 소비자

| 메서드 | 토픽 | 소비 서버 | 처리 내용 |
|--------|------|-----------|-----------|
| `enqueueCompleted()` | `PaymentCompleted` | inventoryserver | `tryConfirmReservedStock()` → 재고 차감 확정 |
| `enqueueCompleted()` | `PaymentCompleted` | orderserver | `processPaymentCompleted()` → 주문 완료 상태 전이 |
| `enqueueFailed()` | `PaymentFailed` | inventoryserver | `releaseReservedStock()` → 예약 재고 해제 |
| `enqueueFailed()` | `PaymentFailed` | orderserver | `processPaymentFailed()` → 결제 실패 상태 전이 |
| `enqueueCancelled()` | `PaymentCancelled` | inventoryserver | `handlePaymentCancelStatus()` → 재고 복구 |

---

## paymentserver가 소비하는 토픽

| 토픽 | 소비자 빈 | 처리 메서드 | 결과 |
|------|-----------|-------------|------|
| `PaymentRequested` | `paymentRequestedConsumer` | `prepareRequestedPayment()` | Payment 레코드 READY 상태로 생성 |
| `OrderCancelled` | `orderCancelledConsumer` | `compensateOrderCancelled()` | 승인된 결제 취소 보상 처리 |

---

## PaymentOutboxService 역할 요약

- **입력 기점**: orderserver `OrderCheckedOut` → inventoryserver 재고 예약 성공 → `PaymentRequested` → paymentserver Payment 준비
- **출력 기점**: Toss API 결과를 `PaymentCompleted` / `PaymentFailed` / `PaymentCancelled`로 발행하여 inventoryserver와 orderserver 양쪽 후속 처리를 동시에 트리거
- **패턴**: Outbox 테이블에 저장 후 Debezium CDC가 Kafka로 발행 (직접 Kafka 발행 없음)
- **파티셔닝 기준**: `aggregate_id = orderId` → orderId 기준 Kafka 파티션 보장으로 동일 주문의 이벤트 순서 유지

---

## 취소 플로우

```
[사용자 주문 취소 요청 or 결제 취소]
        │
[orderserver]
  OrderOutbox → OrderCancelled 발행
        │
        ▼ (Debezium → Kafka)
[paymentserver]
  orderCancelledConsumer 수신
  → compensateOrderCancelled() → Toss 결제 취소 API 호출
  → PaymentOutboxService.enqueueCancelled() → PaymentCancelled 발행
        │
        ▼ (Debezium → Kafka)
[inventoryserver]
  paymentCancelStatusConsumer 수신
  → handlePaymentCancelStatus() → releaseReservedStock() → 재고 복구
```
