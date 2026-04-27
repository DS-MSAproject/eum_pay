# PaymentServer 작업 이력

---

## [2026-04-25] WebClientResponseException 원인 분석 및 임시 수정

### 문제 상황

`PaymentService.confirm()` 메서드의 catch 블록에서 `WebClientResponseException` 발생.
Toss API에 paymentKey·orderId를 전달해 최종 승인을 요청하는 시점에 오류 발생.

---

### 원인 분석

#### 1. 핵심 원인 — `TOSS_SECRET_KEY` 누락

`docker-compose/vault/file/dseum-payment.json`의 `payment.toss.secret-key` 값이
환경변수 플레이스홀더로만 설정되어 있음.

```json
"toss": {
  "secret-key": "${TOSS_SECRET_KEY}"
}
```

`vault-init.sh`는 `docker-compose/vault/file/.env`에서 `TOSS_SECRET_KEY`를 읽어
`vault kv patch secret/dseum-payment "payment.toss.secret-key"=<값>`으로 Vault에 패치하는 구조.

그런데 `.env` 파일에 `TOSS_SECRET_KEY` 항목이 존재하지 않아 패치가 수행되지 않음.
결과적으로 Vault에는 `payment.toss.secret-key = "${TOSS_SECRET_KEY}"` 리터럴 문자열이 저장됨.

**오류 전파 흐름:**

```
Vault → payment.toss.secret-key = "${TOSS_SECRET_KEY}" (리터럴)
  → Spring Boot가 TOSS_SECRET_KEY 프로퍼티 탐색
  → .env에 없으므로 미해소 → 빈 문자열 or 플레이스홀더 그대로 사용
  → TossPaymentsClient.basicAuthHeader("${TOSS_SECRET_KEY}") → 잘못된 Authorization 헤더 생성
  → Toss API 401 Unauthorized → WebClientResponseException
```

#### 2. 부수 버그 — `vault-init.sh`의 `grep "SECRET_KEY"` 충돌 (미수정, 참고용)

`.env`에 `TOSS_SECRET_KEY`가 추가될 경우 아래 라인에서 문제 발생.

```sh
# vault-init.sh:110
SECRET_KEY=$(grep "SECRET_KEY" "$AWS_ENV_PATH" | cut -d'=' -f2 | tr -d '\n\r ')
```

`grep "SECRET_KEY"`가 `AWS_SECRET_KEY`와 `TOSS_SECRET_KEY` 두 줄을 동시에 매칭해
AWS 시크릿 키 변수에 두 값이 이어붙어 AWS 인증이 깨짐.
→ 수정 시 `grep "^AWS_SECRET_KEY="` 로 앵커 처리 필요.

---

### 임시 수정 내역

**파일:** `paymentserver/src/main/resources/application.yml`

Vault가 `payment.toss.secret-key = ${TOSS_SECRET_KEY}`를 주입했을 때 Spring Boot가
`TOSS_SECRET_KEY` 프로퍼티를 환경 전체에서 탐색하는 점을 이용해,
application.yml에 해당 프로퍼티를 직접 선언.

```yaml
# 추가된 내용 (파일 상단 payment 섹션 위)
# TODO: Vault .env에 TOSS_SECRET_KEY 추가 후 이 항목 제거
TOSS_SECRET_KEY: test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6
```

- Vault 실행 환경: `TOSS_SECRET_KEY` 프로퍼티가 탐색되어 플레이스홀더 정상 해소
- Vault 미실행 로컬 환경: `${TOSS_SECRET_KEY:test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6}` 폴백 그대로 동작

---

### 정식 해결 방법 (TODO)

1. `docker-compose/vault/file/.env`에 실제 Toss 시크릿 키 추가
   ```
   TOSS_SECRET_KEY=test_sk_실제값
   ```
2. Docker Compose 재기동 (vault-init.sh 재실행으로 Vault 패치)
3. `paymentserver/src/main/resources/application.yml`의 `TOSS_SECRET_KEY: ...` 라인 제거
4. `vault-init.sh` 110번째 줄 `grep "SECRET_KEY"` → `grep "^AWS_SECRET_KEY="` 수정

---

## [2026-04-25] 403 FORBIDDEN_REQUEST — Toss confirm orderId prefix 누락

### 문제 상황

`PaymentService.confirm()` 호출 시 Toss API에서 403 `FORBIDDEN_REQUEST` 응답.

```
Toss 승인 API 오류: orderId=4429801498258392000, status=403 FORBIDDEN,
body={"code":"FORBIDDEN_REQUEST","message":"허용되지 않은 요청입니다."}
```

### 원인

Toss confirm API에 전송하는 `orderId`는 프론트엔드가 결제 위젯 초기화 시 사용한 값과 정확히 일치해야 함.
프론트엔드는 `order-{orderId}` 형식으로 초기화했으나 백엔드는 숫자 orderId만 전송 → Toss 매칭 실패 → 403.

### 수정 내역

**파일:** `paymentserver/src/main/java/com/eum/paymentserver/service/PaymentService.java`

```java
// Before
TossConfirmRequest tossRequest = TossConfirmRequest.builder()
        .paymentKey(request.getPaymentKey())
        .orderId(String.valueOf(request.getOrderId()))
        .amount(request.getAmount())
        .build();

// After
TossConfirmRequest tossRequest = TossConfirmRequest.builder()
        .paymentKey(request.getPaymentKey())
        .orderId("order-" + request.getOrderId())
        .amount(request.getAmount())
        .build();
```

---

## [2026-04-25] payments 테이블 version 컬럼 NOT NULL 오류

### 문제 상황

결제 준비(`POST /payments/prepare`) 요청 시 500 에러 발생.

```
ERROR: null value in column "version" of relation "payments" violates not-null constraint
```

### 원인

이전 세션에서 `Payment` 엔티티에 `@Version` 필드를 추가했으나, `ddl-auto: update`로 생성된
DB 컬럼에 `DEFAULT 0`이 없어 INSERT 시 null 제약 위반 발생.

### 수정 내역

**파일:** `paymentserver/src/main/java/com/eum/paymentserver/domain/Payment.java`

```java
// Before
@Version
@Column(nullable = false)
private Long version = 0L;

// After
@Version
@Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0")
private Long version = 0L;
```

`@PrePersist`에 null 체크 추가:
```java
@PrePersist
void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.version == null) {
        this.version = 0L;
    }
}
```

기존 DB 컬럼 수동 패치 (PostgreSQL):
```sql
ALTER TABLE payments ALTER COLUMN version SET DEFAULT 0;
UPDATE payments SET version = 0 WHERE version IS NULL;
```

---

## [2026-04-25] TossPaymentsClient 요청 상세 디버그 로그 추가

### 내용

`WebClientResponseException` 원인 분석을 위해 Toss confirm 호출 직전 요청 정보 로깅 추가.

**파일:** `paymentserver/src/main/java/com/eum/paymentserver/client/TossPaymentsClient.java`

```java
log.info("[TOSS-DEBUG] ===== Toss 승인 요청 =====");
log.info("[TOSS-DEBUG] URL            : {}{}", baseUrl, confirmPath);
log.info("[TOSS-DEBUG] Secret-Key     : {}", secretKey);
log.info("[TOSS-DEBUG] Authorization  : {}", authHeader);
log.info("[TOSS-DEBUG] Idempotency-Key: {}", idempotencyKey);
log.info("[TOSS-DEBUG] paymentKey     : {}", request.getPaymentKey());
log.info("[TOSS-DEBUG] orderId        : {}", request.getOrderId());
log.info("[TOSS-DEBUG] amount         : {}", request.getAmount());
```

**파일:** `paymentserver/src/main/java/com/eum/paymentserver/service/PaymentService.java`

`WebClientResponseException` catch 블록에 실제 Toss 응답 로깅 추가:
```java
log.error("Toss 승인 API 오류: orderId={}, status={}, body={}",
        request.getOrderId(), ex.getStatusCode(), ex.getResponseBodyAsString());
```

---

## [2026-04-25] 결제 전체 플로우 정상 동작 확인

### 결과

위 수정 사항 적용 후 전체 Saga 체인 정상 동작 확인.

```
OrderCheckedOut → PaymentRequested → Toss 승인 → PaymentCompleted (SSE: APPROVED)
```

`GET /payments/orders/{orderId}/events` SSE 엔드포인트에서 `status: APPROVED` 이벤트 수신 확인.
