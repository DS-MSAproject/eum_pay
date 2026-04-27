# Docker Compose 정합성 감사 리포트

> 작성일: 2026-04-23  
> 대상 파일: `docker-compose/docker-compose.yml`, `docker-compose/prometheus.yml`  
> 검토 범위: 서비스 포트 정합성, Kafka 환경변수, 의존성 체인, 이미지 태그, Debezium 설정, Observability 연결

---

## 1. 서비스 전체 인벤토리

### 1-1. 인프라 서비스

| 서비스 | 컨테이너명 | 이미지 | 호스트 포트 | 컨테이너 포트 | 네트워크 별칭 | 상태 |
|--------|-----------|--------|------------|-------------|-------------|------|
| `database` | postgres | postgres:16-alpine | 5432 | 5432 | `database`, `postgres` | ✅ healthcheck 있음 |
| `inventory-database` | inventory-postgres | postgres:16-alpine | 5435 | 5432 | `inventory-database`, `inventory-postgres` | ✅ |
| `product-database` | product-postgres | postgres:16-alpine | 5434 | 5432 | `product-database`, `product-postgres` | ✅ |
| `cart-database` | cart-postgres | postgres:16-alpine | 5436 | 5432 | `cart-database`, `cart-postgres` | ✅ |
| `order-database` | order-postgres | postgres:16-alpine | 5437 | 5432 | `order-database`, `order-postgres` | ✅ |
| `review-database` | review-postgres | postgres:16-alpine | 5433 | 5432 | `review-database`, `review-postgres` | ✅ |
| `payment-database` | payment-postgres | postgres:16-alpine | 5438 | 5432 | `payment-database`, `payment-postgres` | ✅ |
| `board-database` | board-postgres | postgres:16-alpine | 5439 | 5432 | `board-database`, `board-postgres` | ✅ |
| `kafka` | kafka | cp-kafka:7.5.0 | 9092, 29092 | 9092, 29092 | `kafka` | ✅ KRaft node 1 |
| `kafka2` | kafka2 | cp-kafka:7.5.0 | 9094, 39092 | 9092, 29092 | `kafka2` | ✅ KRaft node 2 |
| `kafka3` | kafka3 | cp-kafka:7.5.0 | 9096, 49092 | 9092, 29092 | `kafka3` | ✅ KRaft node 3 |
| `redis` | redis | redis:7-alpine | 6379 | 6379 | `redis`, `redisserver` | ✅ |
| `vault` | dseum-vault | hashicorp/vault | 8200 | 8200 | `dseum-vault`, `vaultserver` | ✅ |
| `zipkin` | — | openzipkin/zipkin | 9411 | 9411 | `zipkin-server` | ✅ |
| `elasticsearch` | elasticsearch | elasticsearch:8.18.0 | 9200, 9300 | 9200, 9300 | `elasticsearch` | ✅ healthcheck 있음 |
| `kibana` | kibana | kibana:8.18.0 | 5601 | 5601 | `kibana` | ✅ service_healthy 조건 |
| `logstash` | logstash | logstash:8.18.0 | 5000 | 5000 | `logstash` | ✅ |
| `prometheus` | — | prom/prometheus | 9090 | 9090 | `prometheus` | ✅ |
| `grafana` | — | grafana/grafana | 13000 | 3000 | `grafana` | ✅ |
| `connect` | connect | debezium/connect:2.4 | 8086 | 8086 | — | ✅ healthcheck 있음 |
| `connector-init` | — | curlimages/curl | — | — | — | ✅ one-shot |
| `filebeat` | filebeat | filebeat:8.18.0 | — | — | — | ✅ |

### 1-2. 애플리케이션 서비스

| 서비스명 (compose) | 이미지 태그 | 호스트 포트 | 컨테이너 포트 | 네트워크 별칭 | Prometheus 타깃 |
|------------------|------------|-----------|-------------|-------------|--------------|
| `configserver` | 0.0.1-SNAPSHOT | 8071 | 8071 | `configserver` | configserver:8071 |
| `eurekaserver` | 0.0.1-SNAPSHOT | 8070 | 8070 | `eurekaserver` | eurekaserver:8070 |
| `authserver` | 0.0.1-SNAPSHOT | 8090 | 8090 | `authserver` | authserver:8090 |
| `gatewayserver` | 0.0.1-SNAPSHOT | 8072 | 8072 | `gatewayserver` | gatewayserver:8072 (HTTPS) |
| `dseumorders` | 0.0.2-SNAPSHOT | 8082 | **8989** | `orderserver`, `dseumorders` | dseumorders:8989 |
| `dseumcart` | 0.0.2-SNAPSHOT | 8088 | 8088 | `cartserver`, `dseumcart` | — |
| `dseumpayment` | 0.0.1-SNAPSHOT | 8083 | 8083 | `paymentserver`, `dseumpayment` | dseumpayment:8083 |
| `dseumproducts` | 0.0.1-SNAPSHOT | 8081 | **8989** | `productserver` | productserver:8989 |
| `dseuminventory` | 0.0.1-SNAPSHOT | 8085 | **8989** | `inventoryserver` | inventoryserver:8989 |
| `dseumreview` | 0.0.1-SNAPSHOT | 8098 | 8098 | `reviewserver` | dseumreview:8098 |
| `dseumboard` | 0.0.1-SNAPSHOT | 8084 | 8084 | `boardserver` | boardserver:8084 |
| `dseumrag` | 0.0.1-SNAPSHOT | 8099 | 8099 | `ragserver` | ragserver:8099 |
| `dseumsearch` | 0.0.1-SNAPSHOT | 8087 | 8087 | `dseum-search`, `searchserver` | searchserver:8087 |

> **참고**: `orderserver`, `productserver`, `inventoryserver`는 내부 컨테이너 포트 **8989**로 실행되며, Prometheus는 내부 포트를 직접 타깃으로 설정.

---

## 2. 감사에서 발견된 이슈 및 처리 결과

### 2-1. [수정 완료] 이슈 목록

#### ① inventoryserver Kafka 환경변수 오류
- **파일**: `docker-compose.yml` → `dseuminventory` 서비스
- **원인**: `SPRING_KAFKA_BOOTSTRAP_SERVERS`는 `spring.kafka.bootstrap-servers`를 오버라이드 → Spring Cloud Stream Kafka Binder의 `brokers` 설정(`KAFKA_BOOTSTRAP_SERVERS`)과 다른 프로퍼티
- **결과**: inventoryserver의 `application.yaml`이 `${KAFKA_BOOTSTRAP_SERVERS:...}` 플레이스홀더를 사용하므로 단순 env var 오타였음
- **수정**: `SPRING_KAFKA_BOOTSTRAP_SERVERS` → `KAFKA_BOOTSTRAP_SERVERS`

```yaml
# Before
- SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092,kafka2:9092,kafka3:9092

# After
- KAFKA_BOOTSTRAP_SERVERS=kafka:9092,kafka2:9092,kafka3:9092
```

---

#### ② cart-database `wal_level=logical` 누락
- **파일**: `docker-compose.yml` → `cart-database` 서비스
- **원인**: 다른 DB들은 모두 `command: ["postgres", "-c", "wal_level=logical"]`를 가지고 있었으나 cart-database만 누락
- **영향**: Debezium CDC가 cartserver DB에 연결 시 logical replication 슬롯 생성 실패 가능
- **수정**: `command: ["postgres", "-c", "wal_level=logical"]` 추가

---

#### ③ kibana `depends_on` 헬스 조건 누락
- **파일**: `docker-compose.yml` → `kibana` 서비스
- **원인**: `depends_on: elasticsearch`만 있고 `condition: service_healthy`가 없어, Elasticsearch가 완전히 준비되기 전에 Kibana가 기동 시도
- **영향**: Kibana 초기 기동 실패 및 재시작 반복
- **수정**: `condition: service_healthy` 추가

```yaml
# Before
depends_on:
  - elasticsearch

# After
depends_on:
  elasticsearch:
    condition: service_healthy
```

---

#### ④ authserver 이미지 태그 불일치
- **파일**: `docker-compose.yml` → `authserver` 서비스
- **원인**: `dseum/authserver:latest` 사용 — 다른 서비스들은 전부 `0.0.1-SNAPSHOT` 태그 사용
- **영향**: `latest` 태그는 로컬 빌드 이미지가 없을 경우 Docker Hub pull 시도 → 실패 또는 의도하지 않은 버전 사용
- **수정**: `dseum/authserver:0.0.1-SNAPSHOT`

---

#### ⑤ prometheus.yml volume 경로 trailing slash
- **파일**: `docker-compose.yml` → `prometheus` 서비스
- **원인**: `./prometheus.yml/`처럼 끝에 슬래시가 붙으면 Docker가 파일이 아닌 디렉터리 마운트로 인식
- **영향**: Prometheus 설정 파일을 디렉터리로 마운트 시도 → Prometheus 기동 실패
- **수정**: `./prometheus.yml:/etc/prometheus/prometheus.yml` (슬래시 제거)

---

#### ⑥ prometheus.yml `inventoryerver` 오타
- **파일**: `docker-compose/prometheus.yml` → `inventory` job
- **원인**: `inventoryerver:8989` — 's'가 빠진 오타
- **영향**: Prometheus가 inventoryserver 메트릭 수집 실패 (타깃 DNS 미해석)
- **수정**: `inventoryserver:8989`

---

### 2-2. [관찰 사항] 추가 점검 필요 항목

#### ⑦ searchserver Kafka Binder 브로커 단일 노드 문제
- **파일**: `searchserver/src/main/resources/application.yml`
- **현황**: `spring.cloud.stream.kafka.binder.brokers: kafka:9092` (단일 브로커 하드코딩)
- **docker-compose**: `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092,kafka2:9092,kafka3:9092` 설정 — 하지만 이 env는 `spring.kafka.bootstrap-servers`를 오버라이드하며, Spring Cloud Stream Kafka Binder의 `brokers` 설정을 오버라이드하지 않음
- **위험도**: 낮음 (searchserver의 Kafka function definition이 주석 처리되어 있어 현재 Kafka 미사용 상태)
- **권고**: 활성화 시 `SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS=kafka:9092,kafka2:9092,kafka3:9092`로 변경 또는 application.yml의 `brokers` 플레이스홀더 사용

```yaml
# searchserver application.yml 권장 수정
stream:
  kafka:
    binder:
      brokers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092,kafka2:9092,kafka3:9092}
```

---

#### ⑧ reviewserver gRPC 포트 충돌 가능성 검토
- **파일**: `docker-compose.yml` → `dseumreview`
- **현황**: `AUTH_GRPC_HOST=authserver`, `AUTH_GRPC_PORT=9093`
- **검토**: `9093`은 Kafka KRaft Controller의 내부 리스너 포트와 동일하나, 호스트명이 `authserver`이므로 `authserver:9093`으로 라우팅 → Kafka와 충돌 없음
- **확인 필요**: authserver가 실제로 gRPC를 `9093` 포트에서 서빙하는지 authserver 설정 재확인 필요
- **위험도**: authserver gRPC 포트 불일치 시 reviewserver 기동 실패

---

#### ⑨ eurekaserver `depends_on: database` 의존성 검토
- **현황**: `eurekaserver`가 `database(main postgres):service_healthy`를 의존
- **분석**: eurekaserver는 별도 DB를 사용하지 않으므로 이 의존성은 불필요
- **영향**: 없음 (기동 순서 제어에만 영향, 기능적 문제 없음)

---

#### ⑩ dseumproducts `eurekaserver` 미의존
- **현황**: `dseumproducts`의 `depends_on`에 `eurekaserver`가 없음 (`product-database`, `configserver`만 있음)
- **영향**: productserver가 eurekaserver보다 먼저 기동 시도 가능 — Spring Cloud의 Eureka 재시도 메커니즘으로 자체 복구되나, 초기 기동 로그에 연결 오류 출력
- **위험도**: 낮음 (자동 복구됨)

---

#### ⑪ Vault 헬스체크 없음
- **현황**: `vault` 서비스에 `healthcheck`가 없고 `configserver`는 `vault:service_started`에만 의존
- **영향**: Vault 초기화(`vault-init.sh` 실행)가 완료되기 전에 configserver가 Vault에서 설정 로드 시도 가능
- **위험도**: 중간 — Vault 초기화가 빠르면 문제없으나, 느릴 경우 configserver 기동 실패

```yaml
# 권장 추가
vault:
  healthcheck:
    test: ["CMD", "vault", "status", "-address=http://localhost:8200"]
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 10s
```

---

#### ⑫ filebeat 로그 수집 범위
- **현황**: filebeat가 `./gatewayserver/logs`만 마운트 — 다른 서비스 로그는 수집 미대상
- **설계 의도**: gatewayserver 요청 로그만 ELK로 중앙화하는 의도적 설계로 보임
- **위험도**: 없음 (의도된 설계로 판단)

---

## 3. Kafka 환경변수 정합성 매트릭스

Spring Cloud Stream Kafka Binder는 `KAFKA_BOOTSTRAP_SERVERS` env로 `spring.cloud.stream.kafka.binder.brokers`를 오버라이드.  
Spring Kafka (직접 사용)는 `SPRING_KAFKA_BOOTSTRAP_SERVERS` env로 `spring.kafka.bootstrap-servers`를 오버라이드.

| 서비스 | Kafka 사용 방식 | docker-compose env | application.yml 플레이스홀더 | 정합성 |
|--------|---------------|-------------------|--------------------------|------|
| `dseuminventory` | Spring Cloud Stream | `KAFKA_BOOTSTRAP_SERVERS` | `${KAFKA_BOOTSTRAP_SERVERS:...}` | ✅ |
| `dseumorders` | Spring Cloud Stream | 없음 (config server 의존) | `${KAFKA_BOOTSTRAP_SERVERS:kafka:9092,kafka2:9092,kafka3:9092}` | ✅ 기본값 정상 |
| `dseumpayment` | Spring Cloud Stream | `KAFKA_BOOTSTRAP_SERVERS` | `${KAFKA_BOOTSTRAP_SERVERS:...}` | ✅ |
| `dseumsearch` | Spring Cloud Stream | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `brokers: kafka:9092` (하드코딩) | ⚠️ Kafka 활성화 시 수정 필요 |
| `connect` | 직접 설정 | `BOOTSTRAP_SERVERS`, `CONNECT_BOOTSTRAP_SERVERS` | — | ✅ |

---

## 4. 데이터베이스 — Debezium 커넥터 매핑

`connector-init`이 `connect:8086`에 등록하는 커넥터 목록 (register-connector.sh 기준):

| 커넥터명 | 타깃 DB | DB 호스트 | 테이블 | Outbox 라우팅 기준 |
|---------|--------|---------|------|----------------|
| `dseum-asgard-connector` | dseum_product | product-database | public.products | — (상품 변경 감지) |
| payment outbox connector | dseum_payment | payment-database | public.payment_outbox_event | `event_type` 필드 |
| order outbox connector | dseum_order | order-database | public.order_outbox | `event_type` 필드 |
| inventory outbox connector | dseum_inventory | inventory-database | public.inventory_outbox | `topic` 컬럼 |

> **connector-init 의존성 완전성**: connect + 7개 DB(payment, product, inventory, order, review, board + 기다리는 조건) 모두 healthy 확인 후 실행 → 정상

---

## 5. Prometheus 스크레이핑 정합성

| job_name | 타깃 | 컨테이너 내부 포트 | 프로토콜 | 정합성 |
|---------|------|----------------|---------|------|
| prometheus | prometheus:9090 | 9090 | http | ✅ |
| zipkin | zipkin-server:9411 | 9411 | http | ✅ |
| eureka | eurekaserver:8070 | 8070 | http | ✅ |
| config | configserver:8071 | 8071 | http | ✅ |
| gateway | gatewayserver:8072 | 8072 | **https** | ✅ (insecure_skip_verify) |
| auth | authserver:8090 | 8090 | http | ✅ |
| product | productserver:8989 | 8989 | http | ✅ |
| inventory | inventoryserver:8989 | 8989 | http | ✅ (오타 수정됨) |
| order | dseumorders:8989 | 8989 | http | ✅ |
| payment | dseumpayment:8083 | 8083 | http | ✅ |
| board | boardserver:8084 | 8084 | http | ✅ |
| search | searchserver:8087 | 8087 | http | ✅ |
| rag | ragserver:8099 | 8099 | http | ✅ |
| review | dseumreview:8098 | 8098 | http | ✅ |

> **수집 누락 서비스**: `cartserver`는 Prometheus 스크레이핑 job 없음. 모니터링 필요 시 추가 고려.

---

## 6. 시작 순서 의존성 체인

```
vault → configserver → eurekaserver
                   ↓
              authserver (healthcheck 90s)
                   ↓
              gatewayserver

database (healthcheck) → eurekaserver, authserver, gatewayserver, logstash

각 DB (healthcheck) → 해당 서비스 (orderserver, paymentserver, ...)

kafka → dseumorders, dseumpayment, dseuminventory, dseumcart, dseumsearch
elasticsearch (healthcheck) → kibana, logstash, connect, filebeat, dseumrag

connect (healthcheck) → connector-init
connector-init → (7개 DB 전부 healthy 대기)
```

---

## 7. 종합 판정

| 분류 | 수정 완료 | 관찰/권고 | 정상 |
|------|---------|---------|-----|
| 인프라 설정 오류 | 4건 | 1건 | — |
| 프로메테우스 설정 | 2건 | — | 14개 job |
| Kafka 환경변수 | 1건 | 1건 | 3개 서비스 |
| 이미지 태그 | 1건 | — | 13개 서비스 |
| 의존성 체인 | — | 2건 | — |

**수정 완료 총 6건 / 추가 권고 5건 / 중대 장애 위험 0건**
