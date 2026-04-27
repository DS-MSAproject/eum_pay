# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 빌드 및 실행 명령어

프로젝트 루트(`eum_pay/`)에서 실행:

```bash
# orderserver 단독 빌드
./gradlew :orderserver:build

# 테스트 실행
./gradlew :orderserver:test

# 단일 테스트 클래스 실행
./gradlew :orderserver:test --tests "com.eum.orderserver.SomeTest"

# 테스트 스킵 빌드
./gradlew :orderserver:build -x test

# Jib로 컨테이너 이미지 빌드 (Docker 데몬 불필요)
./gradlew :orderserver:jibDockerBuild

# 컨테이너 레지스트리로 직접 푸시
./gradlew :orderserver:jib
```

## 아키텍처 개요

### 서비스 역할
주문 생성 및 상태 관리를 담당하는 MSA 서비스. 주문 생성은 동기(Feign), 이후 상태 전이는 전부 Kafka 이벤트 기반 비동기로 처리된다.

### 주문 상태 전이 흐름

```
[클라이언트 POST /orders]
        │ (Feign → productserver 상품 검증)
        ▼
ORDER_CHECKED_OUT
        │ Kafka: InventoryReserved / InventoryReservationFailed
        ▼
INVENTORY_RESERVED ──실패──▶ INVENTORY_RESERVATION_FAILED
        │ Kafka: PaymentCompleted / PaymentFailed
        ▼
PAYMENT_COMPLETED ──실패──▶ PAYMENT_FAILED ──▶ INVENTORY_RELEASED / INVENTORY_RELEASE_FAILED
        │ Kafka: InventoryDeducted / InventoryDeductionFailed
        ▼
ORDER_COMPLETED ──실패──▶ INVENTORY_DEDUCTION_FAILED

별도: ORDER_CANCELLED (사용자 DELETE 요청, PAYMENT_COMPLETED 또는 ORDER_COMPLETED 상태에서만 가능)
```

### 이벤트 처리 레이어 구조

Kafka Consumer → `OrderEventConsumerConfig`(Bean 등록) → `OrderEventProcessor`(멱등성 검증) → `OrderService`(상태 전이)

- **멱등성**: `OrderEventLog` 테이블에 `event_key` unique constraint + `ON CONFLICT DO NOTHING` native query로 구현. 동일 이벤트가 재처리되면 `tryRegister()`가 false를 반환하여 즉시 리턴.
- **Outbox 패턴**: 주문 상태가 변경될 때 `OrderOutbox` 테이블에 이벤트를 저장. ORDER_CHECKED_OUT, ORDER_COMPLETED, ORDER_CANCELLED 세 시점에서만 발행.

### 엔티티 구조 및 PK 설계

`Orders` 테이블:
- `id` (Long, PK, auto-generated): DB 내부 surrogate key
- `order_id` (Long, unique, not null): 비즈니스 식별자. `@PrePersist`에서 UUID MSB 기반으로 자동 생성됨

`OrderDetails` 테이블:
- `order_id` FK는 `orders.order_id`(unique 컬럼)를 참조. `@JoinColumn(referencedColumnName = "order_id")`로 명시.

모든 `orderRepository.findById()`는 DB PK(`id`) 조회이므로 비즈니스 로직에서 주문을 찾을 때는 반드시 `orderRepository.findByOrderId()`를 사용해야 한다.

### 외부 서비스 연동

| 연동 방식 | 대상 | 내용 |
|-----------|------|------|
| Feign (동기) | `dseum-product` | `/product/checkout/validate` — 주문 생성 시 상품/가격 검증 |
| Kafka Consumer | inventoryserver | InventoryReserved, InventoryReservationFailed, InventoryDeducted, InventoryDeductionFailed, InventoryReleased, InventoryReleaseFailed |
| Kafka Consumer | paymentserver | PaymentCompleted, PaymentFailed |
| Kafka Producer (Outbox) | 전체 | OrderCheckedOut, OrderCompleted, OrderCancelled |

Kafka Consumer는 모두 파티셔닝(`partitioned: true`) + DLQ 활성화. Consumer Bean 이름과 `application.yml`의 `spring.cloud.function.definition` 목록이 정확히 일치해야 바인딩된다.

### 인바운드 이벤트 역직렬화

`PaymentOrderEvent`, `InventoryReservationEvent` 등 인바운드 메시지는 `@JsonAlias`로 여러 필드명 변형을 허용한다. 타 서비스가 `order_id` 또는 `orderId` 중 어느 이름으로 발행하더라도 수신 가능.

### 설정 소스 우선순위

`application.yml` → Vault (`secret/dseum-order`) → Config Server. DB 접속 정보, 시크릿 등 민감 정보는 Vault에서 주입된다. 로컬 개발 시 `VAULT_TOKEN`, `CONFIGSERVER_URI` 환경변수로 오버라이드 가능.

## 트러블슈팅 기록

### [2026-04-27] POST /orders/subject 405 오류

**증상**: 프론트엔드에서 `POST /api/v1/orders/get` 호출 시 405 Method Not Allowed.

**원인**: 주문 생성 엔드포인트가 `@PostMapping("/get")`으로 잘못 명명되어 있었음. 경로에 `/get`이 포함되어 있어 GET 요청으로 혼동 유발.

**수정**: `OrdersController.java` — `@PostMapping("/get")` → `@PostMapping("/subject")`

---

### [2026-04-27] Feign → productserver 401 오류

**증상**: 주문 생성 시 `ProductCheckoutClient`가 `POST /product/checkout/validate`를 호출하면 401 반환.

**원인 및 수정**: productserver 측 문제. `productserver/CLAUDE.md` 트러블슈팅 참고.
