# 결제 시스템 리팩토링 현황 및 잔여 과제

## 완료된 작업

| # | 내용 | 변경 파일 |
|---|------|-----------|
| 1 | `CancelReasonType.SAGA_COMPENSATION` → `ORDER_CANCELLATION` | `paymentserver/domain/CancelReasonType.java` |
| 2 | `enqueueApproved()` 데드 메서드 및 호출 제거 | `paymentserver/Service/PaymentOutboxService.java` |
| 3 | Toss API 호출을 `@Transactional` 외부로 분리 (3-Phase TransactionTemplate) | `paymentserver/Service/PaymentService.java` |
| 4 | enum 비교 문자열화 제거 (`"APPROVED".equals(...)` → `== PaymentState.APPROVED`) | `paymentserver/Service/PaymentService.java` |
| 5 | `InventoryOrderSagaHandler` → `InventoryOrderEventHandler` (Saga 참조 완전 제거) | `inventoryserver/service/` |
| 6 | Kafka 소비자 Config 핸들러 참조 교체 | `inventoryserver/config/InventoryEventConsumerConfig.java` |
| 7 | **트랜잭션 원자성 복구** — InventoryOrderEventHandler 모든 핸들러에 `@Transactional` 적용, 비즈니스 예외를 Result 객체로 전환해 UnexpectedRollbackException 제거 | `inventoryserver/service/InventoryOrderEventHandler.java`, `InventoryService.java` |
| 8 | **낙관적 락 + idempotencyKey 갱신** — `Payment` 엔티티에 `@Version` 추가, 재시도 시 새 키 발급, `OptimisticLockingFailureException` → 409 처리 | `paymentserver/domain/Payment.java`, `exception/GlobalExceptionHandler.java` |
| 9 | **Toss 장애 대응** — Resilience4j Circuit Breaker + Reactor Timeout 적용, `CallNotPermittedException`·`TimeoutException` catch 추가 | `paymentserver/client/TossPaymentsClient.java`, `Service/PaymentService.java`, `build.gradle`, `application.yml` |
| 10 | **DLQ 설정** — paymentserver·orderserver·inventoryserver 전 소비자 바인딩에 `enable-dlq: true` 적용 | 3개 서비스 `application.yml` |
| 11 | **inventoryserver 단일 브로커 버그 수정** — `kafka:9092` → 3-브로커 클러스터 | `inventoryserver/src/main/resources/application.yaml` |
| 12 | **inventoryserver paymentCancelStatusConsumer 데드 컨슈머 수정** — `paymentCancelStatus-topic` → `PaymentCancelled` (Debezium eventType 기반 라우팅 일치) | `inventoryserver/src/main/resources/application.yaml` |
| 13 | **이중 Outbox 발행 제거** — 레거시 토픽 동시 발행 라인 삭제, canonical 토픽만 발행 | `inventoryserver/service/InventoryOutboxService.java` |
| 14 | **Outbox 스테일 모니터링** — 5분 이상 PENDING 상태 Outbox 이벤트 감지 스케줄러, `@EnableScheduling` 활성화 | `paymentserver/Service/PaymentOutboxMonitor.java`, `EumPaymentApplication.java`, `repository/PaymentOutboxEventRepository.java` |

---

## 잔여 과제 (운영 고려사항)

### [MEDIUM] 1. Toss API 타임아웃 발생 시 결제 상태 미확정

**현황**
Toss API가 타임아웃되면 현재는 Payment를 FAILED로 기록하지만,
Toss가 실제로 결제를 처리했을 가능성이 있어 이중 청구 리스크가 존재한다.

**해결 방향**
`APPROVAL_REQUESTED` 상태를 Phase 2 진입 전에 DB에 기록하고,
주기적 폴링 스케줄러가 Toss 조회 API(`GET /v1/payments/{paymentKey}`)로 최종 상태를 확인·동기화한다.
현재는 Toss가 프론트엔드 SDK 레벨에서 이중 요청을 방어하므로 운영상 허용 가능한 수준.

---

### [LOW] 2. Outbox publishedAt 필드 미갱신

**현황**
`PaymentOutboxEvent.publishedAt`이 Debezium 발행 후에도 갱신되지 않는다.
Debezium의 `mark.published` 옵션이 설정되지 않았거나 delete-mode로 동작 중.

**해결 방향**
Debezium 커넥터 설정에서 `transforms.outbox.route.by.field=eventType` 및
`delete.tombstone.handling.mode` 확인 후 필요 시 `publishedAt` 갱신 로직 추가.

---

## 우선순위 요약

| 우선순위 | 항목 | 이유 |
|----------|------|------|
| 단기 | #1 타임아웃 결제 상태 미확정 | 이중 청구 리스크 (낮지만 실재) |
| 중기 | #2 publishedAt 미갱신 | 모니터링 정확도 향상 |
