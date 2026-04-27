# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 빌드 및 실행 명령어

프로젝트 루트(`eum_pay/`)에서 실행:

```bash
# cartserver 단독 빌드
./gradlew :cartserver:build

# 테스트 스킵 빌드
./gradlew :cartserver:build -x test

# Jib로 컨테이너 이미지 빌드
./gradlew :cartserver:jibDockerBuild

# 컨테이너 레지스트리로 직접 푸시
./gradlew :cartserver:jib
```

## 아키텍처 개요

### 서비스 역할
사용자별 장바구니를 관리하는 MSA 서비스. 상품 추가/수량변경/옵션변경/삭제 및 선택 관리를 담당한다.

### 외부 서비스 연동

| 연동 방식 | 대상 | 내용 |
|-----------|------|------|
| Feign (동기) | `dseum-product` | 상품 옵션 정책 조회 |
| Feign (동기) | `dseum-inventory` | 장바구니 아이템 재고 상태 조회 |
| Kafka Consumer | orderserver | `OrderCheckedOut` — 주문 체크아웃 시 스냅샷 저장 |
| Kafka Consumer | paymentserver | `PaymentCompleted` — 결제 성공 시 장바구니 실제 삭제 |
| Kafka Consumer | paymentserver | `PaymentFailed` — 결제 실패 시 스냅샷만 삭제 (장바구니 유지) |
| Kafka Consumer | orderserver | `OrderCancelled` — 주문 취소 시 스냅샷만 삭제 (장바구니 유지) |

### Kafka Consumer 구조 (2단계 스냅샷 방식)

`OrderEventConsumerConfig` → `CartCheckoutSnapshotService`

```
1단계: OrderCheckedOut 수신
    → cart_checkout_snapshot 테이블에 {orderId, userId, items} 저장
    → 중복 이벤트는 idempotent하게 무시

2단계: PaymentCompleted 수신
    → 스냅샷에서 items 역직렬화
    → CartService.removeOrderedItems() — 항목별 삭제 (없는 항목은 warn 후 skip)
    → 스냅샷 삭제

결제 실패/취소: PaymentFailed 또는 OrderCancelled 수신
    → 스냅샷만 삭제, 장바구니는 그대로 유지
```

**왜 2단계인가**: `PaymentCompleted` 메시지에는 items 정보가 없고 orderId만 있음.
`OrderCheckedOut`에서 items를 미리 저장해두고 PaymentCompleted 시 참조해야 함.

#### 컨슈머 그룹 및 DLQ

| 토픽 | 컨슈머 그룹 | DLQ |
|------|------------|-----|
| `OrderCheckedOut` | `dseum-cart-order-checked-out` | 활성화 |
| `PaymentCompleted` | `dseum-cart-payment-completed` | 활성화 |
| `PaymentFailed` | `dseum-cart-payment-failed` | 활성화 |
| `OrderCancelled` | `dseum-cart-order-cancelled` | 활성화 |

### 설정 소스 우선순위

`application.yml` → Vault (`secret/dseum-cart`) → Config Server. DB 접속 정보는 Vault에서 주입된다.
Vault `dseum-cart.json`에는 DB/JPA 설정만 있으며 Kafka 관련 설정은 없다.

## 트러블슈팅 기록

### [2026-04-27] 결제 후 장바구니 미삭제 문제

**증상**: 주문/결제가 완료된 후에도 장바구니에 해당 상품이 그대로 남아 있음.

**원인**: 다음 3가지가 모두 누락되어 있었음.
1. `build.gradle` — Kafka 의존성이 주석 처리 (`// Template leftovers...`)
2. `application.yml` — `OrderCheckedOut` 토픽 consumer 바인딩 설정 없음
3. `OrderCheckedOut` 이벤트를 수신해 장바구니 항목을 삭제하는 consumer 코드 없음

**흐름**:
```
주문 생성 (POST /orders/subject)
    → orderserver가 order_outbox에 OrderCheckedOut 저장
    → Debezium이 읽어 Kafka OrderCheckedOut 토픽에 발행
    → cartserver 수신 → 주문된 items만 장바구니에서 제거
```

**수정 파일**:
| 파일 | 내용 |
|------|------|
| `build.gradle` | `spring-cloud-starter-stream-kafka`, `spring-cloud-stream-binder-kafka` 의존성 주석 해제 |
| `application.yml` | 4개 토픽 consumer 바인딩 추가 (OrderCheckedOut/PaymentCompleted/PaymentFailed/OrderCancelled) |
| `message/OrderCheckedOutMessage.java` | 신규 생성 — 이벤트 역직렬화 DTO |
| `message/PaymentCompletedMessage.java` | 신규 생성 — 결제 완료 이벤트 DTO |
| `message/PaymentFailedMessage.java` | 신규 생성 — 결제 실패 이벤트 DTO |
| `message/OrderCancelledMessage.java` | 신규 생성 — 주문 취소 이벤트 DTO |
| `config/OrderEventConsumerConfig.java` | 재작성 — 4개 Kafka consumer 빈 (스냅샷 방식) |
| `domain/CartCheckoutSnapshot.java` | 신규 생성 — 스냅샷 JPA 엔티티 |
| `repository/CartCheckoutSnapshotRepository.java` | 신규 생성 — 스냅샷 레포지터리 |
| `service/CartCheckoutSnapshotService.java` | 신규 생성 — 스냅샷 저장/삭제/장바구니 연동 |
| `service/CartService.java` | `removeOrderedItems()` 추가 — 항목별 예외 처리 |
| `dto/CartItemDeleteRequest.java` | `@AllArgsConstructor` 추가 |
| `db/migration/V6__add_cart_checkout_snapshot.sql` | 신규 생성 — `cart_checkout_snapshot` 테이블 |
| `docker-compose/docker-compose.yml` | cartserver 환경변수에 `KAFKA_BOOTSTRAP_SERVERS` 추가 |

**재배포**: `./gradlew :cartserver:jibDockerBuild` 후 컨테이너 재시작 필요.

**정상 배포 확인 체크리스트** (재시작 후 로그에서 확인):
- `"Found 3 JPA repository interfaces"` (CartCheckoutSnapshotRepository 포함)
- `"Current version of schema \"public\": 6"` (V6 마이그레이션 실행 완료)
- `"orderCheckedOutConsumer"`, `"paymentCompletedConsumer"` 등 Kafka 컨슈머 초기화 로그
