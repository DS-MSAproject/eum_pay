# 세션 전체 수정 상세 리포트

> 작성일: 2026-04-23  
> 작성자: Claude Sonnet 4.6 (시니어 MSA 리드 역할)  
> 범위: 결제 시스템 프로덕션 레디 리팩토링 + 인프라 정합성 감사

---

## Part 1 — 결제 시스템 리팩토링

### Step 1. 트랜잭션 원자성 복구

#### 문제
`InventoryOrderEventHandler`에서 재고 예약과 Outbox 저장이 별개 트랜잭션으로 실행되어,
Outbox 저장 실패 시 재고만 예약되고 이벤트가 발행되지 않는 정합성 깨짐 발생.
또한 `reserveOrderStock()` 내부에서 비즈니스 예외가 던져지면 Spring 프록시가
트랜잭션을 rollback-only로 마킹해 `UnexpectedRollbackException` 전파.

#### 수정 파일 1: `inventoryserver/service/InventoryOrderEventHandler.java`

```java
// Before — @Transactional 없음, 재고·Outbox가 별개 TX
public void handleOrderCheckedOut(OrderCheckedOutEvent event) {
    try {
        ProductReservationResult result = inventoryService.reserveOrderStock(event); // TX1
        inventoryOutboxService.enqueueReservationResult(result);                    // TX2
    } catch (Exception e) {
        // 예외 처리 후 보상 이벤트 발행 시도 → 이미 TX rollback-only 상태
    }
}

// After — @Transactional 단일 경계, 예외 없이 Result 객체 반환
@Transactional
public void handleOrderCheckedOut(OrderCheckedOutEvent event) {
    String eventId = event.processedEventId();
    if (processedEventRepository.existsByEventId(eventId)) { return; }
    ProductReservationResult result = inventoryService.reserveOrderStock(event);
    inventoryOutboxService.enqueueReservationResult(result);
    if (result.isSuccess()) {
        inventoryOutboxService.enqueuePaymentRequested(event);
    }
    markProcessed(eventId, "ORDER_CHECKED_OUT");
}
```

모든 핸들러(`handlePaymentCompletedTopic`, `handlePaymentFailedTopic`, `handlePaymentCancelStatus`)에 `@Transactional` 추가.

#### 수정 파일 2: `inventoryserver/service/InventoryService.java`

```java
// Before — 비즈니스 실패 시 예외 throw → TX rollback-only 마킹
public ProductReservationResult reserveOrderStock(OrderCheckedOutEvent event) {
    if (재고_없음) throw new IllegalStateException("재고 부족");
    // ...
}

// After — 비즈니스 실패를 Result 객체로 반환, 예외는 인프라 장애만
public ProductReservationResult reserveOrderStock(OrderCheckedOutEvent event) {
    try {
        // 예약 로직
        return ProductReservationResult.success(...);
    } catch (IllegalArgumentException | IllegalStateException e) {
        return ProductReservationResult.failure(orderId, e.getMessage());
    }
}
```

`confirmReservedStock()` 제거, `tryConfirmReservedStock(Long orderId)` 신규 추가:
- 예약 없음 → `InventoryDeductionResult.failure` 반환
- 이미 확정됨 → 멱등성 성공 반환
- 해제/거절 상태 → failure 반환

#### 삭제 파일: `inventoryserver/service/InventoryOrderSagaHandler.java`
Saga 패턴 명칭 제거. `InventoryOrderEventHandler`로 역할 통합.

#### 수정 파일 3: `inventoryserver/config/InventoryEventConsumerConfig.java`
`InventoryOrderSagaHandler` → `InventoryOrderEventHandler` 참조 교체.

---

### Step 2. 낙관적 락 + idempotencyKey 재발급

#### 문제
- `Payment` 엔티티에 `@Version` 없음 → 동시 `confirm()` 요청 시 Lost Update 발생
- `refreshPrepareContext()` 에서 실패 후 재시도 시 동일 `idempotencyKey` 재사용
  → Toss가 이전 실패 응답을 캐시해 재시도가 실제로 실행되지 않을 수 있음

#### 수정 파일 1: `paymentserver/domain/Payment.java`

```java
// 추가된 필드
@Version
@Column(nullable = false)
private Long version = 0L;  // 낙관적 락 — 동시 confirm 요청 Lost Update 방지
```

```java
// refreshPrepareContext() 수정 — 재시도 시 새 키 발급
public void refreshPrepareContext(Long amount, String currency) {
    this.amount = amount;
    this.currency = currency;
    if (this.status == PaymentState.FAILED || this.status == PaymentState.CANCEL_FAILED) {
        this.status = PaymentState.READY;
        this.failureCode = null;
        this.failureMessage = null;
        this.failedAt = null;
        this.idempotencyKey = UUID.randomUUID().toString(); // 신규 추가
    }
}
```

#### 수정 파일 2: `paymentserver/exception/GlobalExceptionHandler.java`

```java
// 신규 추가 — 낙관적 락 충돌 시 409 반환
@ExceptionHandler(OptimisticLockingFailureException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public Map<String, Object> handleOptimisticLock(OptimisticLockingFailureException exception) {
    return baseBody(HttpStatus.CONFLICT, "동시 요청이 충돌했습니다. 잠시 후 다시 시도해주세요.");
}
```

---

### Step 3. Circuit Breaker + Timeout (Toss API 장애 대응)

#### 문제
- CB 없음 → Toss 장애 시 모든 결제 요청 연쇄 실패
- Timeout 없음 → Toss 무응답 시 WebFlux 스레드 무한 점유
- CB OPEN 및 Timeout 예외를 catch하지 않아 500 에러 비처리

#### 수정 파일 1: `paymentserver/build.gradle`

```gradle
// 신규 추가
implementation 'io.github.resilience4j:resilience4j-spring-boot3'
implementation 'io.github.resilience4j:resilience4j-reactor'
```

#### 수정 파일 2: `paymentserver/client/TossPaymentsClient.java`

```java
// Before — 단순 WebClient 호출, CB/Timeout 없음
public Mono<TossPaymentResponse> confirm(String idempotencyKey, TossConfirmRequest request) {
    return webClient.post().uri(...).bodyValue(request)
            .retrieve().bodyToMono(TossPaymentResponse.class);
}

// After — PaymentPolicyProperties 주입, Timeout + CircuitBreaker 체이닝
@PostConstruct
void init() {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("toss");
    this.webClient = WebClient.builder()...build();
}

public Mono<TossPaymentResponse> confirm(String idempotencyKey, TossConfirmRequest request) {
    return webClient.post().uri(...)
            .header("Idempotency-Key", idempotencyKey)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TossPaymentResponse.class)
            .timeout(Duration.ofSeconds(paymentPolicyProperties.getApproveTimeoutSeconds()))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
}
// cancel()도 동일 패턴 적용
```

> **수정 포인트**: 기존 `tossPaymentProperties.getConfirmTimeoutSeconds()` 호출(존재하지 않는 메서드)을
> `PaymentPolicyProperties.getApproveTimeoutSeconds()`로 교체해 컴파일 오류 수정.

#### 수정 파일 3: `paymentserver/src/main/resources/application.yml`

```yaml
# 신규 추가
resilience4j:
  circuitbreaker:
    instances:
      toss:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.reactive.function.client.WebClientResponseException$InternalServerError
          - org.springframework.web.reactive.function.client.WebClientResponseException$BadGateway
          - org.springframework.web.reactive.function.client.WebClientResponseException$ServiceUnavailable
          - org.springframework.web.reactive.function.client.WebClientResponseException$GatewayTimeout
        ignore-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException$BadRequest
          - org.springframework.web.reactive.function.client.WebClientResponseException$UnprocessableEntity
```

CB 정책: 4xx(클라이언트 오류)는 카운트 무시, 5xx/Timeout/네트워크 오류만 실패로 카운트.

#### 수정 파일 4: `paymentserver/Service/PaymentService.java`

```java
// 신규 import 추가
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import reactor.core.Exceptions;
import java.util.concurrent.TimeoutException;

// confirm() / cancelApprovedPayment() 양쪽에 추가된 catch 블록
} catch (CallNotPermittedException ex) {
    // CB OPEN — DB 상태 변경 없이 즉시 503 반환 (결제 미시도 상태 유지)
    log.warn("Toss 결제 승인 차단됨 (CB OPEN): orderId={}", request.getOrderId());
    throw new IllegalStateException("결제 서비스가 일시적으로 사용 불가합니다. 잠시 후 다시 시도해주세요.");
} catch (Exception ex) {
    if (Exceptions.unwrap(ex) instanceof TimeoutException) {
        // Timeout — FAILED 기록 + Outbox + SSE 발행
        Payment saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow();
            p.fail("TIMEOUT", "Toss API 응답 시간 초과");
            paymentAttemptRepository.save(PaymentAttempt.of(p, "CONFIRM", writeJson(tossRequest), "TIMEOUT", "TIMEOUT"));
            Payment persisted = paymentRepository.save(p);
            paymentOutboxService.enqueueFailed(persisted);
            return persisted;
        }));
        paymentSseService.publishFailed(saved);
        throw new IllegalStateException("결제 서비스 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
    }
    log.error("Toss 승인 중 예기치 않은 오류: orderId={}", request.getOrderId(), ex);
    throw new IllegalStateException("결제 처리 중 오류가 발생했습니다.");
}
```

---

### Step 4. DLQ 설정 (Kafka 소비자 메시지 유실 방지)

#### 문제
DLQ 미설정 시 소비자 처리 실패 메시지가 무한 재시도하거나 조용히 드랍됨.

#### 구조 수정 공통 사항
기존에 `spring.cloud.stream.kafka.bindings`와 `spring.cloud.stream.bindings`가
혼재되어 있던 구조를 올바르게 분리:
- `spring.cloud.stream.bindings` → 목적지(destination)·그룹(group) 등 공통 설정
- `spring.cloud.stream.kafka.bindings` → Kafka 전용 설정 (DLQ 등)

#### 수정 파일 1: `paymentserver/src/main/resources/application.yml`

```yaml
# 2개 소비자 DLQ 추가
spring:
  cloud:
    stream:
      bindings:
        paymentRequestedConsumer-in-0:
          destination: PaymentRequested
          group: dseum-payment-requested
        orderCancelledConsumer-in-0:
          destination: OrderCancelled
          group: dseum-payment-order-cancelled
      kafka:
        binder:
          brokers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092,kafka2:9092,kafka3:9092}
        bindings:
          paymentRequestedConsumer-in-0:
            consumer:
              enable-dlq: true
              dlq-partitions: 1
              auto-commit-on-error: true
          orderCancelledConsumer-in-0:
            consumer:
              enable-dlq: true
              dlq-partitions: 1
              auto-commit-on-error: true
```

#### 수정 파일 2: `orderserver/src/main/resources/application.yml`

8개 소비자 전체 DLQ 추가 (바인딩 구조 분리 포함):
`inventoryReservedConsumer`, `inventoryReservationFailedConsumer`,
`paymentCompletedConsumer`, `paymentFailedConsumer`,
`inventoryDeductedConsumer`, `inventoryDeductionFailedConsumer`,
`inventoryReleasedConsumer`, `inventoryReleaseFailedConsumer`

#### 수정 파일 3: `inventoryserver/src/main/resources/application.yaml`

4개 소비자 전체 DLQ 추가 (아래 추가 수정 사항도 포함):

| 수정 항목 | Before | After |
|-----------|--------|-------|
| Kafka 브로커 | `kafka:9092` (단일 브로커) | `kafka:9092,kafka2:9092,kafka3:9092` |
| `paymentCancelStatusConsumer` 토픽 | `paymentCancelStatus-topic` | `PaymentCancelled` |
| DLQ | 없음 | 전 소비자 `enable-dlq: true` |

**`paymentCancelStatus-topic` 수정 근거**:
`register-connector.sh`의 `payment-outbox-connector` 설정에서
`"transforms.outbox.route.by.field": "event_type"`으로 라우팅되어
Debezium이 `PaymentCancelled`를 Kafka 토픽명으로 발행함.
기존 `paymentCancelStatus-topic` 구독은 실질적으로 **데드 컨슈머** 상태.

---

### Step 5. InventoryOutboxService 이중 발행 제거

#### 문제
`enqueueReservationResult()`, `enqueueDeductionResult()`, `enqueueReleaseResult()`가
레거시 토픽과 canonical 토픽에 **동시에 두 개** Outbox 레코드를 저장해
Kafka에 동일 이벤트가 두 번 발행됨.

#### 수정 파일: `inventoryserver/service/InventoryOutboxService.java`

삭제된 레거시 상수:
```java
// 삭제
private static final String PRODUCT_RESERVATION_STATUS_TOPIC = "productReservationStatus-topic";
private static final String PRODUCT_RESTORE_STATUS_TOPIC = "productRestoreStatus-topic";
private static final String INVENTORY_DEDUCTION_STATUS_TOPIC = "inventoryDeductionStatus-topic";
private static final String INVENTORY_RELEASE_STATUS_TOPIC = "inventoryReleaseStatus-topic";
```

수정된 발행 로직 (4개 메서드):
```java
// Before — 이중 저장
saveOrderOutbox(event.getOrderId(), eventType, PRODUCT_RESERVATION_STATUS_TOPIC, payload); // 레거시
saveOrderOutbox(event.getOrderId(), eventType, eventType, payload);                         // canonical

// After — canonical 단일 저장
saveOrderOutbox(event.getOrderId(), eventType, eventType, payload);
```

`enqueueRestoreResult()`: 기존 `PRODUCT_RESTORE_STATUS_TOPIC` 하드코딩 → `eventType` 동적 사용으로 변경.

---

### Step 6. Outbox 모니터링 (Debezium 장애 조기 탐지)

#### 문제
Debezium 커넥터 장애로 Outbox 이벤트가 Kafka에 발행되지 않아도 감지 수단 없음.

#### 신규 파일 1: `paymentserver/Service/PaymentOutboxMonitor.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxMonitor {

    private static final int STALE_THRESHOLD_MINUTES = 5;
    private final PaymentOutboxEventRepository paymentOutboxEventRepository;

    @Scheduled(fixedDelay = 300_000)  // 5분마다 실행
    @Transactional(readOnly = true)
    public void checkStaleOutboxEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<PaymentOutboxEvent> stale = paymentOutboxEventRepository
                .findByStatusAndCreatedAtBefore(OutboxEventStatus.PENDING, threshold);

        if (stale.isEmpty()) return;

        log.warn("[OUTBOX-ALERT] {}개의 PENDING 아웃박스 이벤트가 {}분 이상 미발행 상태입니다. Debezium 커넥터를 확인하세요.",
                stale.size(), STALE_THRESHOLD_MINUTES);

        stale.forEach(event -> log.warn("[OUTBOX-ALERT] stale event: id={}, eventType={}, aggregateId={}, createdAt={}",
                event.getId(), event.getEventType(), event.getAggregateId(), event.getCreatedAt()));
    }
}
```

#### 수정 파일 2: `paymentserver/EumPaymentApplication.java`

```java
@SpringBootApplication
@EnableScheduling  // 신규 추가
public class EumPaymentApplication { ... }
```

#### 수정 파일 3: `paymentserver/repository/PaymentOutboxEventRepository.java`

```java
// 신규 쿼리 메서드 추가
List<PaymentOutboxEvent> findByStatusAndCreatedAtBefore(OutboxEventStatus status, LocalDateTime threshold);
```

---

### 기타 코드 정리

#### `paymentserver/Service/PaymentOutboxService.java`
- `enqueueApproved()` 데드 메서드 삭제 (소비자 없는 `PaymentApproved` 이벤트 발행 코드)
- `resolvePaymentStatus()` switch에서 `"PaymentApproved" -> "PAYAPPROVED"` case 삭제

#### `paymentserver/domain/CancelReasonType.java`
```java
// Before
SAGA_COMPENSATION  // Saga 패턴 미사용 프로젝트에 불일치 명칭

// After
ORDER_CANCELLATION
```

#### `paymentserver/Service/PaymentService.java`
- enum 문자열 비교 제거: `"APPROVED".equals(status)` → `payment.getStatus() == PaymentState.APPROVED`
- `prepareRequestedPayment()`에서 누락된 `paymentRepository.save(payment)` 복구 (신규 결제 미저장 버그)
- `compensateOrderCancelled()`에서 `SAGA_COMPENSATION` → `ORDER_CANCELLATION` 참조 교체

---

## Part 2 — Docker-compose / 인프라 정합성 수정

### 수정 파일: `docker-compose/docker-compose.yml`

#### ① `dseuminventory` 잘못된 Kafka 환경변수명

| | Before | After |
|---|--------|-------|
| 환경변수 | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `KAFKA_BOOTSTRAP_SERVERS` |

**이유**: `SPRING_KAFKA_BOOTSTRAP_SERVERS`는 `spring.kafka.bootstrap-servers`에 매핑되어
Spring Cloud Stream binder에 영향을 주지 않음.
inventoryserver의 `application.yaml`은 `${KAFKA_BOOTSTRAP_SERVERS:...}` placeholder를 사용.

#### ② `cart-database` `wal_level=logical` 누락

```yaml
# 추가됨
cart-database:
  command: ["postgres", "-c", "wal_level=logical"]
```

나머지 7개 DB 모두 설정되어 있었으나 cart-database만 누락.
향후 Debezium 확장 시 DB 재시작 없이 복제 슬롯 생성 가능.

#### ③ `kibana` `depends_on` 누락

```yaml
# 추가됨
kibana:
  depends_on:
    elasticsearch:
      condition: service_healthy
```

Elasticsearch가 완전히 기동되기 전에 Kibana가 연결을 시도해 초기화 실패 가능성 제거.

#### ④ `authserver` 이미지 태그 고정

```yaml
# Before
image: dseum/authserver:latest

# After
image: dseum/authserver:0.0.1-SNAPSHOT
```

`latest` 태그는 배포 시점마다 다른 이미지를 가리킬 수 있어 장애 재현 불가.

#### ⑤ `prometheus.yml` volume trailing slash 제거

```yaml
# Before — 파일이 아닌 디렉터리로 마운트될 수 있음
volumes:
  - ./prometheus.yml/:/etc/prometheus/prometheus.yml

# After
volumes:
  - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

#### ⑥ searchserver 주석 오류 제거

```yaml
# Before — 실제 포트(8087)와 맞지 않는 오류 주석
- "8087:8087" # 호스트 8084 : 컨테이너 8083 ...

# After
- "8087:8087"
```

---

### 수정 파일: `docker-compose/prometheus.yml`

#### inventory job 서비스명 오타 수정

```yaml
# Before — 's' 누락으로 메트릭 수집 불가
- targets: [ "inventoryerver:8989" ]

# After
- targets: [ "inventoryserver:8989" ]
```

---

### 수정 파일: `inventoryserver/src/main/resources/application.yaml`

#### `server.port` 불일치 수정

```yaml
# Before — docker-compose(8085:8989)와 불일치
server:
  port: 8085

# After — productserver/orderserver(8989)와 패턴 일치
server:
  port: 8989
```

**배경**: docker-compose는 `"8085:8989"` (호스트8085→컨테이너8989)로 매핑하고,
prometheus.yml도 `inventoryserver:8989`로 스크레이핑.
앱이 8085로 기동되면 외부 포트 포워딩 불가, Prometheus 메트릭 수집 실패.

---

## Part 3 — 신규 생성 파일

| 파일 | 설명 |
|------|------|
| `CLAUDE.md` | 프로젝트 개요·페르소나·기술스택·서비스목록 |
| `payment-flow-architecture.md` | 결제 플로우 참여 서비스·Kafka 토픽·Debezium 커넥터 명세 |
| `payment-refactoring-todo.md` | 리팩토링 완료 항목 및 잔여 과제 추적 |
| `payment-system-architecture.md` | Mermaid 다이어그램 5개 (시스템 구성·시퀀스·이벤트체이닝·CB·개선요약) |
| `diagrams/01-system-architecture.png` | 전체 시스템 구성도 PNG |
| `diagrams/02-payment-confirm-flow.png` | 결제 확정 3-Phase TX + CB 시퀀스 PNG |
| `diagrams/03-event-chain.png` | 이벤트 체이닝 플로우 PNG |
| `diagrams/04-circuit-breaker.png` | CB 상태 전이 PNG |
| `diagrams/05-improvements.png` | 개선 전후 비교 PNG |
| `session-changes-report.md` | 본 문서 |

---

## 전체 수정 파일 목록

| 서비스 | 파일 | 변경 유형 |
|--------|------|-----------|
| paymentserver | `domain/Payment.java` | 수정 (`@Version`, idempotencyKey 재발급) |
| paymentserver | `domain/CancelReasonType.java` | 수정 (SAGA_COMPENSATION → ORDER_CANCELLATION) |
| paymentserver | `Service/PaymentService.java` | 수정 (3-Phase TX, enum 비교, CB/Timeout catch, save 복구) |
| paymentserver | `Service/PaymentOutboxService.java` | 수정 (enqueueApproved 제거) |
| paymentserver | `Service/PaymentOutboxMonitor.java` | **신규 생성** |
| paymentserver | `client/TossPaymentsClient.java` | 수정 (CB + Timeout, PaymentPolicyProperties 주입) |
| paymentserver | `exception/GlobalExceptionHandler.java` | 수정 (OptimisticLockingFailureException 409) |
| paymentserver | `repository/PaymentOutboxEventRepository.java` | 수정 (findByStatusAndCreatedAtBefore 추가) |
| paymentserver | `EumPaymentApplication.java` | 수정 (@EnableScheduling) |
| paymentserver | `build.gradle` | 수정 (resilience4j 의존성 추가) |
| paymentserver | `src/main/resources/application.yml` | 수정 (Resilience4j CB 설정, DLQ 설정, 바인딩 구조 분리) |
| inventoryserver | `service/InventoryService.java` | 수정 (비즈니스 예외 → Result 반환, tryConfirmReservedStock 추가) |
| inventoryserver | `service/InventoryOrderEventHandler.java` | 수정 (@Transactional 전체 적용, Saga 제거) |
| inventoryserver | `service/InventoryOutboxService.java` | 수정 (이중 발행 제거, 레거시 상수 삭제) |
| inventoryserver | `config/InventoryEventConsumerConfig.java` | 수정 (핸들러 참조 교체) |
| inventoryserver | `service/InventoryOrderSagaHandler.java` | **삭제** |
| inventoryserver | `src/main/resources/application.yaml` | 수정 (server.port 8085→8989, 단일 브로커→3브로커, 토픽 수정, DLQ) |
| orderserver | `src/main/resources/application.yml` | 수정 (DLQ 8개 소비자, 바인딩 구조 분리) |
| docker-compose | `docker-compose.yml` | 수정 (5개 항목: 환경변수명, wal_level, kibana 의존성, 이미지태그, 주석) |
| docker-compose | `prometheus.yml` | 수정 (inventory 오타, trailing slash) |
