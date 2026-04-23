# 결제 플로우 아키텍처

## 참여 서비스

| 구성요소 | 종류 | 역할 |
|----------|------|------|
| `paymentserver` | 마이크로서비스 | 결제 생명주기 관리 (준비/승인/취소), SSE 실시간 상태 전달 |
| `orderserver` | 마이크로서비스 | 주문 생성 및 상태 관리, 결제 이벤트 구독 |
| `inventoryserver` | 마이크로서비스 | 재고 예약/차감/해제, 결제 요청 이벤트 발행 |
| `gatewayserver` | 마이크로서비스 | API 진입점 라우팅 |
| **Toss Payments API** | 외부 API | 결제 승인/취소 (`/v1/payments/confirm`, `/v1/payments/{key}/cancel`) |
| **Apache Kafka** | 인프라 | 서비스 간 비동기 메시지 브로커 (KRaft 모드, 3-broker 클러스터) |
| **Debezium CDC** | 인프라 | PostgreSQL Outbox 테이블 → Kafka 자동 발행 (3개 커넥터) |
| **PostgreSQL** | 인프라 | `dseum_payment`, `dseum_order`, `dseum_inventory` — Logical Replication 활성화 |

## 핵심 패턴

- **Outbox Pattern** — 각 서비스 DB의 outbox 테이블에 INSERT → Debezium CDC가 Kafka로 발행 (이중 쓰기 없음)
- **SSE (Server-Sent Events)** — 클라이언트에 실시간 결제 상태 스트리밍
- **Idempotency Key** — Toss API 중복 요청 방지
- **TX 분리 (confirm/cancel)** — Toss 외부 API 호출 전후로 트랜잭션을 분리해 DB 커넥션 점유 최소화

> Saga 패턴 미사용. 분산 트랜잭션은 Outbox Pattern 기반 이벤트 체이닝으로만 처리.

## Kafka 토픽 흐름

```
OrderCheckedOut            (orderserver → inventoryserver)          재고 예약 요청
InventoryReserved          (inventoryserver → orderserver)          재고 예약 완료
InventoryReservationFailed (inventoryserver → orderserver)          재고 예약 실패
PaymentRequested           (inventoryserver → paymentserver)        결제 실행 트리거
PaymentCompleted           (paymentserver → orderserver, inventoryserver) 결제 성공
PaymentFailed              (paymentserver → orderserver, inventoryserver) 결제 실패
PaymentCancelled           (paymentserver → inventoryserver)        결제 취소
OrderCancelled             (orderserver → paymentserver)            주문 취소 → 결제 취소 트리거
InventoryDeducted          (inventoryserver → orderserver)          재고 차감 완료
InventoryDeductionFailed   (inventoryserver → orderserver)          재고 차감 실패
InventoryReleased          (inventoryserver → orderserver)          재고 해제 완료
InventoryReleaseFailed     (inventoryserver → orderserver)          재고 해제 실패
paymentCancelStatus-topic  (paymentserver → inventoryserver)        결제 취소 상태
```

## Debezium 커넥터

| 커넥터 | 감시 테이블 | 발행 토픽 |
|--------|------------|-----------|
| `dseum-payment-outbox-connector` | `dseum_payment.public.outbox_events` | `event_type` 필드 기반 라우팅 |
| `dseum-order-outbox-connector` | `dseum_order.public.order_outbox` | `event_type` 필드 기반 라우팅 |
| `dseum-inventory-outbox-connector` | `dseum_inventory.public.inventory_outbox` | `topic` 필드 기반 라우팅 |
