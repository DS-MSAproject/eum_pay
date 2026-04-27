# 결제 시스템 아키텍처 다이어그램

---

## 1. 전체 시스템 구성도

```mermaid
flowchart TB
    Client["🖥️ Client\nFrontend + Toss JS SDK"]

    subgraph GW["API Gateway"]
        direction TB
        gatewayserver["gatewayserver :8072"]
    end

    subgraph PAY["paymentserver :8083"]
        direction TB
        PaySvc["PaymentService\n3-Phase TransactionTemplate"]
        TossClient["TossPaymentsClient\nCircuit Breaker · Timeout"]
        PayMon["PaymentOutboxMonitor\n@Scheduled 5분마다 PENDING 감시"]
        PayDB[("PostgreSQL\noutbox_events\npayments\n...")]
    end

    subgraph TOSS_EXT["외부 PG"]
        TossAPI["🏦 Toss Payments API\nhttps://api.tosspayments.com"]
    end

    subgraph INV["inventoryserver :8085"]
        direction TB
        InvHandler["InventoryOrderEventHandler\n@Transactional"]
        InvOutbox[("PostgreSQL\ninventory_outbox\ninventory_reservations\n...")]
    end

    subgraph ORD["orderserver"]
        direction TB
        OrdConsumer["OrderEventConsumer"]
        OrdDB[("PostgreSQL\norders\n...")]
    end

    subgraph CDC["CDC — Debezium"]
        DebPay["payment-outbox\nconnector"]
        DebInv["inventory-outbox\nconnector"]
    end

    subgraph KAFKA["Apache Kafka — KRaft 3 Brokers"]
        direction LR
        subgraph PAY_T["Payment Topics"]
            T_PR["PaymentRequested"]
            T_PC["PaymentCompleted"]
            T_PF["PaymentFailed"]
            T_PCAN["PaymentCancelled"]
        end
        subgraph ORD_T["Order Topics"]
            T_OCO["OrderCheckedOut"]
            T_OC["OrderCancelled"]
        end
        subgraph INV_T["Inventory Topics"]
            T_IR["InventoryReserved"]
            T_IRF["InventoryReservationFailed"]
            T_ID["InventoryDeducted"]
            T_IDF["InventoryDeductionFailed"]
            T_IREL["InventoryReleased"]
            T_IRELF["InventoryReleaseFailed"]
        end
        subgraph DLQ_T["DLQ Topics (error.원본토픽.컨슈머그룹)"]
            DLQ["각 소비자별 DLQ 자동 생성"]
        end
    end

    Client --> gatewayserver --> PaySvc
    PaySvc <--> TossClient
    TossClient <-->|"HTTPS + CB"| TossAPI
    PaySvc --> PayDB
    PayMon -.->|"감시 (WARN 로그)"| PayDB

    PayDB --> DebPay
    DebPay --> T_PC & T_PF & T_PCAN

    T_PR -->|"DLQ 보호"| PaySvc
    T_OC -->|"DLQ 보호"| PaySvc

    T_OCO -->|"DLQ 보호"| InvHandler
    T_PC & T_PF & T_PCAN -->|"DLQ 보호"| InvHandler

    InvHandler --> InvOutbox
    InvOutbox --> DebInv
    DebInv --> T_PR & T_IR & T_IRF & T_ID & T_IDF & T_IREL & T_IRELF

    T_IR & T_IRF & T_PC & T_PF & T_ID & T_IDF & T_IREL & T_IRELF -->|"DLQ 보호"| OrdConsumer
    OrdConsumer --> OrdDB
```

---

## 2. 결제 확정 플로우 (3-Phase TX + Circuit Breaker)

```mermaid
sequenceDiagram
    autonumber
    participant C  as 클라이언트
    participant GW as gatewayserver
    participant PS as PaymentService
    participant DB as PostgreSQL
    participant CB as CircuitBreaker (toss)
    participant Toss as Toss API
    participant OB as outbox_events
    participant DEB as Debezium
    participant K  as Kafka

    C->>GW: POST /payments/{orderId}/confirm
    GW->>PS: confirm(paymentKey, orderId, amount)

    rect rgb(220, 240, 255)
        Note over PS,DB: Phase 1 — 단기 읽기 TX (DB 커넥션 즉시 반환)
        PS->>DB: findByOrderId() + 금액 검증
        DB-->>PS: Payment (READY)
    end

    rect rgb(255, 245, 200)
        Note over PS,Toss: Phase 2 — DB 커넥션 미점유, 외부 API 호출
        PS->>CB: 호출 허가 확인

        alt CB OPEN — 실패율 ≥ 50% (슬라이딩 윈도우 10회)
            CB-->>PS: CallNotPermittedException
            PS-->>C: 503 일시 사용 불가
        else CB CLOSED / HALF-OPEN
            PS->>Toss: POST /v1/payments/confirm (timeout: 10s)

            alt 성공
                Toss-->>PS: TossPaymentResponse (DONE)
                rect rgb(220, 255, 220)
                    Note over PS,OB: Phase 3 — 성공 기록 TX (@Version 낙관적 락)
                    PS->>DB: payment.approve() + version 검증
                    PS->>OB: PaymentCompleted 이벤트 저장
                    PS-->>C: 200 결제 완료
                end
            else Toss 오류 (4xx / 5xx)
                Toss-->>PS: WebClientResponseException
                rect rgb(255, 220, 220)
                    Note over PS,OB: Phase 3 — 실패 기록 TX
                    PS->>DB: payment.fail(errorCode, msg)
                    PS->>OB: PaymentFailed 이벤트 저장
                    PS-->>C: 400 / 500 오류
                end
            else Reactor Timeout (10s 초과)
                Toss-->>PS: TimeoutException
                rect rgb(255, 220, 220)
                    Note over PS,OB: Phase 3 — 타임아웃 실패 기록 TX
                    PS->>DB: payment.fail("TIMEOUT")
                    PS->>OB: PaymentFailed 이벤트 저장
                    PS-->>C: 503 응답 시간 초과
                end
            end
        end
    end

    Note over OB,K: Debezium CDC — outbox_events INSERT 감지 → Kafka 발행
    DEB->>OB: CDC polling
    DEB->>K: PaymentCompleted / PaymentFailed 토픽 발행
```

---

## 3. 전체 이벤트 체이닝 플로우 (주문 체크아웃 → 최종 상태)

```mermaid
flowchart LR
    subgraph ORD["orderserver"]
        CO["주문 체크아웃\n(OrderCheckedOut 발행)"]
        OS_RES["재고 예약 결과 반영"]
        OS_PAY["결제 완료 반영"]
        OS_DED["재고 차감 반영\n→ ORDER_COMPLETED"]
        OS_FAIL["주문 실패 처리\n→ ORDER_FAILED"]
    end

    subgraph INV["inventoryserver"]
        IR["재고 예약\n(InventoryReserved 발행)"]
        IRF["예약 실패\n(InventoryReservationFailed)"]
        PR["결제 요청 전달\n(PaymentRequested 발행)"]
        ID["재고 차감 확정\n(InventoryDeducted 발행)"]
        IDF["차감 실패\n(InventoryDeductionFailed)"]
        IREL["재고 해제\n(InventoryReleased 발행)"]
    end

    subgraph PAY["paymentserver"]
        PAY_PREP["결제 준비\n(Payment READY)"]
        PAY_CONF["결제 승인\n← Toss API"]
        PAY_FAIL["결제 실패"]
        PAY_CAN["결제 취소\n← Toss API"]
    end

    CO -->|"OrderCheckedOut"| IR
    CO -->|"OrderCheckedOut"| IRF

    IR -->|"InventoryReserved"| OS_RES
    IRF -->|"InventoryReservationFailed"| OS_FAIL

    IR -->|"PaymentRequested"| PAY_PREP
    PAY_PREP -->|"프론트엔드 confirm 호출"| PAY_CONF
    PAY_CONF -->|"PaymentCompleted"| OS_PAY
    PAY_CONF -->|"PaymentCompleted"| ID
    PAY_CONF -.->|"PaymentFailed"| PAY_FAIL
    PAY_FAIL -->|"PaymentFailed"| IREL
    IREL -->|"InventoryReleased"| OS_FAIL

    ID -->|"InventoryDeducted"| OS_DED
    IDF -->|"InventoryDeductionFailed"| OS_FAIL

    OS_PAY -->|"OrderCancelled"| PAY_CAN
    PAY_CAN -->|"PaymentCancelled"| IREL

    style CO fill:#4A90D9,color:#fff
    style PAY_CONF fill:#27AE60,color:#fff
    style OS_DED fill:#27AE60,color:#fff
    style OS_FAIL fill:#E74C3C,color:#fff
    style PAY_FAIL fill:#E74C3C,color:#fff
```

---

## 4. Circuit Breaker 상태 전이

```mermaid
stateDiagram-v2
    [*] --> CLOSED : 초기 상태

    CLOSED --> CLOSED : 호출 성공\n(슬라이딩 윈도우 10회 중 성공)

    CLOSED --> OPEN : 실패율 ≥ 50%\n(최소 5회 호출 후 판정)\n대상: IOException · TimeoutException\n      WebClient 5xx · BadGateway · GatewayTimeout\n무시: 4xx (Bad Request · Unprocessable)

    OPEN --> OPEN : CallNotPermittedException\n→ 즉시 503 반환\n대기 시간: 30s

    OPEN --> HALF_OPEN : 30초 경과 후\n3회 프로브 호출 허용

    HALF_OPEN --> CLOSED : 3회 모두 성공\n→ 정상 복귀

    HALF_OPEN --> OPEN : 1회라도 실패\n→ 다시 30초 대기
```

---

## 5. 개선 사항 요약

```mermaid
flowchart LR
    subgraph BEFORE["🔴 개선 전 문제점"]
        direction TB
        B1["재고 예약 + Outbox 저장\n별도 TX → 정합성 불보장"]
        B2["Toss API에 CB · Timeout 없음\n장애 시 전체 결제 마비"]
        B3["Kafka 소비자 DLQ 없음\n메시지 유실 탐지 불가"]
        B4["낙관적 락 없음\n동시 confirm → Lost Update"]
        B5["idempotencyKey 재사용\n재시도 결제 실패 가능"]
        B6["Outbox 이중 발행\n레거시 토픽 + canonical 토픽"]
        B7["paymentCancelStatusConsumer\n실재하지 않는 토픽 구독"]
        B8["단일 Kafka 브로커\ninventoryserver kafka:9092"]
    end

    subgraph AFTER["🟢 개선 후"]
        direction TB
        A1["@Transactional 단일 경계\n재고 + Outbox 원자적 처리"]
        A2["Resilience4j CB + Reactor Timeout\nCB OPEN → 즉시 503, 빠른 실패"]
        A3["enable-dlq: true\n14개 소비자 전체 DLQ 보호"]
        A4["@Version 낙관적 락\n동시 요청 409 Conflict 반환"]
        A5["refreshPrepareContext()\n재시도 시 새 idempotencyKey 발급"]
        A6["canonical 토픽만 단일 발행\n레거시 라인 완전 제거"]
        A7["PaymentCancelled 토픽 구독\nDebezium 라우팅과 일치"]
        A8["3-브로커 클러스터 적용\n고가용성 확보"]
    end

    subgraph MON["🔵 모니터링 추가"]
        M1["PaymentOutboxMonitor\n5분마다 PENDING 이벤트 감시\nDebezium 장애 조기 탐지"]
    end

    B1 --> A1
    B2 --> A2
    B3 --> A3
    B4 --> A4
    B5 --> A5
    B6 --> A6
    B7 --> A7
    B8 --> A8
```
