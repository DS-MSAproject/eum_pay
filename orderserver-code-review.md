# orderserver 코드 리뷰 리포트

> 작성일: 2026-04-23  
> 검토 범위: orderserver 전체 Java 소스 (32개 파일)

---

## 1. 발견된 문제 요약

| 번호 | 심각도 | 파일 | 문제 |
|------|--------|------|------|
| 1 | **Critical** | `OrderService.java` | `handleInventoryReservationFailed()` — 상태 guard 없음 |
| 2 | **Critical** | `OrderService.java` | `handleInventoryDeducted()` — `PAYMENT_COMPLETED` guard 없음 |
| 3 | **Critical** | `OrderService.java` | `handlePaymentCompleted()` — `INVENTORY_RESERVED` guard 없음 |
| 4 | **High** | `OrderService.java` | 이벤트 핸들러 8개 — `@Transactional` 미선언 |
| 5 | **High** | `OrdersController.java` | `POST /orders` — `userId`를 클라이언트 바디에서 신뢰 (보안) |
| 6 | **Medium** | `OrderService.java` | `register()` — 불필요한 `throws Exception` + 내부 try-catch |
| 7 | **Medium** | `OrderService.java` | `requestCancel()` — `@Transactional` + `selectOrder()` 낭비 |
| 8 | **Medium** | `OrderService.java` | `handleInventoryDeductionFailed()` — 상태 guard 없음 |
| 9 | **Low** | `OrderRequest.java` | 수신자 필드 3개 — `@NotBlank` 미선언 |
| 10 | **Low** | `OrderHistoryService.java` | `Collectors.toList()` 사용 + 중복 `@Transactional` |

---

## 2. Critical 버그

### 2-1. `handleInventoryReservationFailed()` — 상태 guard 없음

**파일**: `service/OrderService.java`

```java
// Before (버그): 어떤 상태든 무조건 INVENTORY_RESERVATION_FAILED로 덮어씀
public void handleInventoryReservationFailed(InventoryReservationEvent event) {
    Orders order = orderRepository.findById(event.getOrderId())...;
    order.setOrderState(OrderState.INVENTORY_RESERVATION_FAILED); // guard 없음!
    log.warn(...)
}
```

**위험 시나리오**: 이벤트 지연/재전송으로 인해 이미 `PAYMENT_COMPLETED` 상태인 주문이
`INVENTORY_RESERVATION_FAILED`로 되돌아갈 수 있음 → 결제는 됐는데 주문이 실패 상태가 되는 심각한 불일치.

```java
// After (수정): ORDER_CHECKED_OUT 상태일 때만 처리
@Transactional
public void handleInventoryReservationFailed(InventoryReservationEvent event) {
    Orders order = orderRepository.findById(event.getOrderId())...;

    if (order.getOrderState() != OrderState.ORDER_CHECKED_OUT) {
        log.info("{}번 주문은 재고 예약 실패 처리 대상 상태가 아닙니다: {}", event.getOrderId(), order.getOrderState());
        return;
    }

    order.setOrderState(OrderState.INVENTORY_RESERVATION_FAILED);
    log.warn("{}번 주문 재고 예약 실패: {}", event.getOrderId(), event.getReason());
}
```

---

### 2-2. `handleInventoryDeducted()` — `PAYMENT_COMPLETED` guard 없음

**파일**: `service/OrderService.java`

```java
// Before (버그): ORDER_COMPLETED 중복만 체크, 정상 선행 상태 미검증
public void handleInventoryDeducted(InventoryDeductionEvent event) {
    Orders order = ...;
    if (order.getOrderState() == OrderState.ORDER_COMPLETED) { return; }
    order.setOrderState(OrderState.ORDER_COMPLETED); // PAYMENT_FAILED 상태에서도 실행됨!
    outboxService.enqueueOrderCompleted(...);
}
```

**위험 시나리오**: 결제 실패로 `PAYMENT_FAILED` 상태인 주문에 `InventoryDeducted` 이벤트가
늦게 도착하면 `ORDER_COMPLETED`로 전이 + `OrderCompleted` 이벤트 발행 → 유령 주문 완료 처리.

```java
// After (수정): PAYMENT_COMPLETED → ORDER_COMPLETED 전이만 허용
@Transactional
public void handleInventoryDeducted(InventoryDeductionEvent event) {
    Orders order = ...;
    if (order.getOrderState() == OrderState.ORDER_COMPLETED) {
        log.info("{}번 주문은 이미 완료 상태입니다.", order.getOrderId());
        return;
    }
    if (order.getOrderState() != OrderState.PAYMENT_COMPLETED) {
        log.warn("{}번 주문은 재고 차감 완료 처리 대상 상태가 아닙니다: {}", order.getOrderId(), order.getOrderState());
        return;
    }
    order.setOrderState(OrderState.ORDER_COMPLETED);
    outboxService.enqueueOrderCompleted(...);
}
```

---

### 2-3. `handlePaymentCompleted()` — `INVENTORY_RESERVED` guard 없음

**파일**: `service/OrderService.java`

기존 코드에는 `handlePaymentCompleted`에 상태 guard가 전혀 없었음.  
`ORDER_CHECKED_OUT` 상태에서 `PAYMENT_COMPLETED`로 직접 점프 가능 → 재고 예약 없이 결제 완료 처리.

```java
// After (수정): INVENTORY_RESERVED 상태일 때만 처리
@Transactional
public void handlePaymentCompleted(PaymentOrderEvent event) {
    ...
    if (order.getOrderState() != OrderState.INVENTORY_RESERVED) {
        log.info("{}번 주문은 결제 완료 처리 대상 상태가 아닙니다: {}", orderId, order.getOrderState());
        return;
    }
    order.setOrderState(OrderState.PAYMENT_COMPLETED);
    ...
}
```

---

## 3. High 심각도 문제

### 3-1. 이벤트 핸들러 8개 — `@Transactional` 미선언

**파일**: `service/OrderService.java`

```
// Before: 모든 handle* 메서드에 @Transactional 없음
public void handleInventoryReserved(...)       { ... }
public void handleInventoryReservationFailed(...) { ... }
public void handlePaymentCompleted(...)        { ... }
// ... 8개 모두
```

**문제점**:
- OrderService를 단독 호출하면 트랜잭션 없이 실행됨 (DB flush 타이밍 불확실)
- `@Transactional`이 없으면 Hibernate dirty-check가 트랜잭션 경계에서 flush되지 않을 위험
- OrderEventProcessor의 트랜잭션에 암묵적으로 의존하는 설계 → 결합도 문제

```java
// After: 8개 모두 @Transactional 추가
@Transactional public void handleInventoryReserved(...)          { ... }
@Transactional public void handleInventoryReservationFailed(...) { ... }
@Transactional public void handlePaymentCompleted(...)           { ... }
@Transactional public void handlePaymentFailed(...)              { ... }
@Transactional public void handleInventoryDeducted(...)          { ... }
@Transactional public void handleInventoryDeductionFailed(...)   { ... }
@Transactional public void handleInventoryReleased(...)          { ... }
@Transactional public void handleInventoryReleaseFailed(...)     { ... }
```

> **참고**: `OrderEventProcessor`가 이미 `@Transactional`이므로, handle* 메서드의
> `@Transactional`은 PROPAGATION.REQUIRED로 합류 — 원자성 보장에 영향 없음.
> Idempotency INSERT + 상태 변경이 동일 트랜잭션 내에서 원자적으로 처리됨.

---

### 3-2. `POST /orders` — 클라이언트 바디의 `userId` 신뢰 (보안)

**파일**: `controller/OrdersController.java`

```java
// Before (보안 취약점): 클라이언트가 임의의 userId를 바디에 실어 전송 가능
@PostMapping
public ResponseEntity<?> order(@Valid @RequestBody OrderRequest orderRequest) {
    Orders savedOrder = orderService.register(orderRequest);
    // orderRequest.getUserId() = 클라이언트가 제공한 값 (위조 가능!)
}
```

**문제**: 게이트웨이가 인증 후 `X-User-Id` 헤더를 주입하는 아키텍처인데,
주문 생성만 헤더를 무시하고 바디의 `userId`를 사용함.
타 사용자의 userId로 주문을 생성하거나 이력을 오염시킬 수 있음.

```java
// After (수정): 게이트웨이 주입 헤더 사용
@PostMapping
public ResponseEntity<?> order(
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody OrderRequest orderRequest) {
    Orders savedOrder = orderService.register(orderRequest, userId);
    ...
}
```

`OrderService.register()`도 `Long userId` 파라미터를 받도록 변경:

```java
// Before
public Orders register(OrderRequest request) throws Exception { ... }

// After
@Transactional
public Orders register(OrderRequest request, Long userId) { ... }
```

---

## 4. Medium 심각도 문제

### 4-1. `register()` — 불필요한 `throws Exception` + 내부 try-catch

```java
// Before: @Transactional 내부에서 Exception을 catch 후 re-throw
// → 원본 예외 타입 소멸, 컨트롤러가 타입별 분기 불가
@Transactional(rollbackFor = Exception.class)
public Orders register(OrderRequest request) throws Exception {
    try {
        ...
    } catch (Exception e) {
        log.error("주문 생성 중 오류: {}", e.getMessage());
        throw new Exception("주문 처리 실패", e); // Exception으로 타입 소멸
    }
}
```

`FeignException`, `IllegalArgumentException`, `IllegalStateException`이 모두 `Exception`으로
래핑되어 컨트롤러에서 구분 불가 → 모두 500으로 응답됨.

```java
// After: 내부 try-catch 제거, @Transactional만으로 롤백 처리
@Transactional
public Orders register(OrderRequest request, Long userId) {
    // 예외는 그대로 전파 → 컨트롤러에서 타입별 처리 가능
}
```

컨트롤러도 예외 타입별 분기 처리로 개선:
```java
} catch (IllegalArgumentException | IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
} catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("주문 처리 실패");
}
```

---

### 4-2. `requestCancel()` — `@Transactional` + `selectOrder()` 낭비

```java
// Before: 즉시 예외를 던지는 메서드에 불필요한 DB 조회 + @Transactional
@Transactional
public void requestCancel(Long orderId) {
    selectOrder(orderId);   // DB SELECT → 결과 미사용
    throw new IllegalStateException("주문 취소 saga는 현재...");
}
```

```java
// After: 즉시 예외만 던짐
public void requestCancel(Long orderId) {
    throw new IllegalStateException("주문 취소는 현재 이벤트 흐름에서 비활성화되어 있습니다.");
}
```

---

### 4-3. `handleInventoryDeductionFailed()` — 상태 guard 없음

```java
// Before: 상태 무관하게 INVENTORY_DEDUCTION_FAILED로 덮어씀
public void handleInventoryDeductionFailed(InventoryDeductionEvent event) {
    Orders order = ...;
    order.setOrderState(OrderState.INVENTORY_DEDUCTION_FAILED); // guard 없음!
}

// After: PAYMENT_COMPLETED 상태일 때만 처리
@Transactional
public void handleInventoryDeductionFailed(InventoryDeductionEvent event) {
    Orders order = ...;
    if (order.getOrderState() != OrderState.PAYMENT_COMPLETED) {
        log.info("{}번 주문은 재고 차감 실패 처리 대상 상태가 아닙니다: {}",
                event.getOrderId(), order.getOrderState());
        return;
    }
    order.setOrderState(OrderState.INVENTORY_DEDUCTION_FAILED);
}
```

---

## 5. Low 심각도 문제

### 5-1. `OrderRequest` — 수신자 필드 검증 없음

```java
// Before: receiver 3개 필드 검증 없음 → 빈 문자열/null 주문 생성 가능
private String receiverName;
private String receiverPhone;
private String receiverAddr;

// After: @NotBlank 추가
@NotBlank
@JsonProperty("receiver_name")
private String receiverName;

@NotBlank
@JsonProperty("receiver_phone")
private String receiverPhone;

@NotBlank
@JsonProperty("receiver_addr")
private String receiverAddr;
```

---

### 5-2. `OrderHistoryService` — 중복 `@Transactional` + `Collectors.toList()`

```java
// Before: 클래스에 @Transactional(readOnly = true) 선언되어 있는데
//          getAllHistory()에 동일 어노테이션 중복 선언
@Transactional(readOnly = true)   // ← 클래스 레벨
public class OrderHistoryService {

    @Transactional(readOnly = true)  // ← 중복 (제거)
    public List<OrderItemHistoryResponse> getAllHistory(Long userId) { ... }

    ...
    .collect(Collectors.toList());  // Java 11 이하 스타일 (변경)
}

// After
public List<OrderItemHistoryResponse> getAllHistory(Long userId) { ... }

    ...
    .toList();   // Java 16+ 불변 List (일관성 유지)
```

---

## 6. 정상 확인 항목

| 항목 | 상태 |
|------|------|
| Outbox 패턴 — Orders save + OrderOutbox save 단일 트랜잭션 | ✅ |
| Idempotency — `ON CONFLICT DO NOTHING` native query | ✅ |
| `OrderEventProcessor` — idempotency check + 비즈니스 로직 동일 트랜잭션 | ✅ |
| `handlePaymentFailed()` — RELEASED/RELEASE_FAILED/ORDER_COMPLETED 다중 guard | ✅ |
| `handleInventoryReleased()` — RELEASED/ORDER_COMPLETED 중복·충돌 guard | ✅ |
| DLQ (`enable-dlq: true`, `dlq-partitions: 1`) 모든 consumer 설정 | ✅ |
| `partitioned: true` + `instance-count/index` 외부화 | ✅ |
| `OrderDetails.snapshotJson` — JSONB 타입, checkoutSnapshot 저장 | ✅ |
| `findAllByUserId` — `join fetch d.orders` N+1 방지 | ✅ |
| `listOrders()` — `Math.max(0, page)` 음수 페이지 방어 | ✅ |
| Feign client timeout / circuit-breaker — paymentserver에는 설정됨 | ✅ |

---

## 7. 수정 파일 목록

| 파일 | 변경 내용 |
|------|---------|
| `service/OrderService.java` | `register()` 시그니처 변경, 내부 try-catch 제거; 이벤트 핸들러 8개 `@Transactional` 추가; 4개 메서드 상태 guard 추가; `requestCancel()` 간소화 |
| `controller/OrdersController.java` | `POST /orders` — `@RequestHeader("X-User-Id")` 추가, 예외 타입별 분기 처리 |
| `dto/OrderRequest.java` | `receiverName/Phone/Addr`에 `@NotBlank` 추가 |
| `service/OrderHistoryService.java` | `getAllHistory()` 중복 `@Transactional` 제거; `Collectors.toList()` → `.toList()` |

---

## 8. 상태 전이 정상 경로 (수정 후)

```
ORDER_CHECKED_OUT
    ↓ InventoryReserved          → INVENTORY_RESERVED
    ↓ InventoryReservationFailed → INVENTORY_RESERVATION_FAILED

INVENTORY_RESERVED
    ↓ PaymentCompleted  → PAYMENT_COMPLETED
    ↓ PaymentFailed     → PAYMENT_FAILED

PAYMENT_COMPLETED
    ↓ InventoryDeducted         → ORDER_COMPLETED
    ↓ InventoryDeductionFailed  → INVENTORY_DEDUCTION_FAILED

PAYMENT_FAILED (또는 보상 트리거)
    ↓ InventoryReleased      → INVENTORY_RELEASED
    ↓ InventoryReleaseFailed → INVENTORY_RELEASE_FAILED
```

각 상태 전이 핸들러가 선행 상태를 검증하므로 순서 역전·재전송으로 인한 상태 오염이 방지됨.
