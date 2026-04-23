# Kafka 파티션 키 운영 전략 리포트

> 작성일: 2026-04-23  
> 검토 배경: 결제 시스템 Kafka 파티션 키 불일치 → 최상의 파티션 운영 코드 적용

---

## 1. 파티션 키가 의미를 갖는 시점

Kafka 파티션 키의 효과는 **소비자 인스턴스 수에 따라 완전히 달라진다.**

### 단일 인스턴스 (현재)

```
파티션 0 ──┐
파티션 1 ──┼──▶ [서비스 인스턴스 1개]  ← 모든 파티션을 혼자 처리
파티션 2 ──┘

파티션 키가 어떤 값이든 동일한 프로세스로 들어오므로 키 값은 무의미.
```

### 수평 확장 (2인스턴스 예시)

```
파티션 0 ──▶ [인스턴스 A]  (0 % 2 == 0)
파티션 1 ──▶ [인스턴스 B]  (1 % 2 == 1)
파티션 2 ──▶ [인스턴스 A]  (2 % 2 == 0)

orderId=100 → hash → 파티션 0 → 항상 인스턴스 A
orderId=200 → hash → 파티션 1 → 항상 인스턴스 B
→ 같은 주문의 이벤트는 반드시 같은 인스턴스가 처리 → 순서·상태 일관성 보장
```

**결론**: 파티션 키는 수평 확장 시에만 실질적 효과를 발휘한다.  
단, 확장 시점에 파티션 키가 올바르지 않으면 재설계가 필요하므로 **지금 올바르게 세팅해두는 것이 맞다.**

---

## 2. 수정 전 문제점

### 문제 1 — payment outbox 파티션 키 불일치

| Outbox | Kafka 파티션 키 |
|--------|----------------|
| order_outbox | `orderId` ✅ |
| inventory_outbox | `orderId` ✅ |
| outbox_events (payment) | `paymentId` (내부 UUID) ⚠️ |

→ 같은 주문에 대한 이벤트임에도 payment 계열 이벤트만 다른 파티션으로 라우팅될 수 있었다.

### 문제 2 — 소비자에 `partitioned: true` 미선언

```yaml
# 수정 전 — 파티션 인식 설정 없음
bindings:
  paymentCompletedConsumer-in-0:
    destination: PaymentCompleted
    group: dseum-order-payment-completed
```

Spring Cloud Stream Kafka Binder가 `partitioned: true` 없이 동작하면,  
수평 확장 시 파티션 배분을 Kafka 기본 전략(라운드로빈)에 맡긴다.  
→ 같은 orderId의 이벤트가 서로 다른 인스턴스로 분산될 수 있다.

### 문제 3 — 토픽 파티션 수 기본값 1

```yaml
# 수정 전 Kafka 브로커 — num.partitions 미설정 → 기본값 1
```

토픽 파티션이 1개면 인스턴스가 여러 개여도 1개만 실제로 메시지를 받는다.  
파티션 키와 `partitioned: true` 모두 소용없어진다.

---

## 3. 적용한 최상의 파티션 운영 코드

### 3-1. Kafka 브로커 — 기본 파티션 수 3으로 설정

**파일: `docker-compose/docker-compose.yml` (kafka, kafka2, kafka3 모두)**

```yaml
# 추가
KAFKA_NUM_PARTITIONS: 3
```

- 신규 토픽은 기본 3파티션으로 생성됨
- replication factor도 3 (이미 설정되어 있음) → 파티션당 3개 복제본
- 3 브로커 × 3 파티션 → 각 브로커가 파티션 1개씩 리더 담당

> DLQ 토픽은 `dlq-partitions: 1`로 명시 설정되어 있어 영향 없음.

### 3-2. 소비자 — `partitioned: true` + 인스턴스 수 외부화

**공통 패턴 (3개 서비스 동일)**

```yaml
spring:
  cloud:
    stream:
      instance-count: ${STREAM_INSTANCE_COUNT:1}   # 총 인스턴스 수 (기본 1)
      instance-index: ${STREAM_INSTANCE_INDEX:0}   # 이 인스턴스 번호 (기본 0)
      bindings:
        paymentCompletedConsumer-in-0:
          destination: PaymentCompleted
          group: dseum-order-payment-completed
          consumer:
            partitioned: true                      # 파티션 인식 활성화
```

#### 파티션 배분 동작 원리

```
instance-count=1, instance-index=0 (현재 단일 인스턴스):
  파티션 0: 0 % 1 == 0 → 이 인스턴스 담당 ✅
  파티션 1: 1 % 1 == 0 → 이 인스턴스 담당 ✅
  파티션 2: 2 % 1 == 0 → 이 인스턴스 담당 ✅
  → 전체 파티션 처리 (현재와 동일한 동작)

instance-count=2, instance-index=0 (수평 확장 시):
  파티션 0: 0 % 2 == 0 → 이 인스턴스 담당 ✅
  파티션 1: 1 % 2 ≠ 0  → 다른 인스턴스 담당
  파티션 2: 2 % 2 == 0 → 이 인스턴스 담당 ✅

instance-count=2, instance-index=1 (수평 확장 시):
  파티션 0: 0 % 2 ≠ 1  → 다른 인스턴스 담당
  파티션 1: 1 % 2 == 1 → 이 인스턴스 담당 ✅
  파티션 2: 2 % 2 ≠ 1  → 다른 인스턴스 담당
```

### 3-3. docker-compose — 인스턴스 환경변수 기본값 주입

**파일: `docker-compose/docker-compose.yml`**

```yaml
# dseumorders, dseumpayment, dseuminventory 각각 추가
- STREAM_INSTANCE_COUNT=1
- STREAM_INSTANCE_INDEX=0
```

현재는 기본값(1/0)이지만, 수평 확장 시 이 값만 변경하면 된다.

### 3-4. payment outbox — 파티션 키를 orderId로 통일 (이전 수정)

**파일: `paymentserver/.../PaymentOutboxService.java`**

```java
// Before: paymentId (내부 UUID) → 파티션 키 불일치
PaymentOutboxEvent.pending(payment.getPaymentId(), ...)

// After: orderId → 전체 흐름 파티션 키 통일
PaymentOutboxEvent.pending(String.valueOf(payment.getOrderId()), ...)
```

---

## 4. 수정 후 전체 구조

### 파티션 키 현황 (통일 완료)

```
[모든 이벤트: orderId 기준 동일 파티션]

OrderCheckedOut      (order_outbox     → aggregate_id = orderId)
InventoryReserved    (inventory_outbox → aggregate_id = orderId)
PaymentRequested     (inventory_outbox → aggregate_id = orderId)
PaymentCompleted     (payment outbox   → aggregate_id = orderId) ← 수정됨
PaymentFailed        (payment outbox   → aggregate_id = orderId) ← 수정됨
PaymentCancelled     (payment outbox   → aggregate_id = orderId) ← 수정됨
InventoryDeducted    (inventory_outbox → aggregate_id = orderId)
InventoryReleased    (inventory_outbox → aggregate_id = orderId)
```

### 수평 확장 시나리오 (준비 완료)

```yaml
# 2인스턴스 확장 시 docker-compose override 또는 k8s env:
# 인스턴스 A
- STREAM_INSTANCE_COUNT=2
- STREAM_INSTANCE_INDEX=0

# 인스턴스 B
- STREAM_INSTANCE_COUNT=2
- STREAM_INSTANCE_INDEX=1
```

코드 변경 없이 환경변수만 바꿔 파티션 배분 활성화.

---

## 5. 수정 파일 목록

| 파일 | 변경 내용 |
|------|---------|
| `docker-compose/docker-compose.yml` | kafka/kafka2/kafka3에 `KAFKA_NUM_PARTITIONS: 3` 추가 |
| `docker-compose/docker-compose.yml` | dseumorders, dseumpayment, dseuminventory에 `STREAM_INSTANCE_COUNT=1`, `STREAM_INSTANCE_INDEX=0` 추가 |
| `orderserver/src/main/resources/application.yml` | `instance-count`, `instance-index` 외부화, 8개 consumer에 `partitioned: true` 추가 |
| `inventoryserver/src/main/resources/application.yaml` | `instance-count`, `instance-index` 외부화, 4개 consumer에 `partitioned: true` 추가 |
| `paymentserver/src/main/resources/application.yml` | `instance-count`, `instance-index` 외부화, 2개 consumer에 `partitioned: true` 추가 |
| `paymentserver/.../PaymentOutboxService.java` | `aggregate_id` = `orderId` (파티션 키 통일) |

---

## 6. 현재 vs 수평 확장 비교

| 항목 | 현재 (단일 인스턴스) | 수평 확장 시 |
|------|-------------------|------------|
| 파티션 수 | 3 (신규 적용) | 3 유지 |
| `partitioned: true` | 선언됨 (대기 상태) | 실제 동작 |
| `instance-count` | 1 | 확장 인스턴스 수 |
| `instance-index` | 0 | 인스턴스마다 0, 1, 2... |
| 환경변수 변경 필요 | 없음 | `STREAM_INSTANCE_COUNT`, `STREAM_INSTANCE_INDEX` 만 수정 |
| 코드 변경 필요 | **없음** | **없음** |
