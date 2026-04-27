# 코드 정리 로그 (Dead Code / 미사용 코드 제거)

> 이 파일은 리팩토링 과정에서 제거된 데드 코드, 미사용 클래스/패키지를 추적합니다.

---

## 2026-04-23

### [inventoryserver] `com.eum.saga.inventory` 패키지 전체 삭제

| 항목 | 내용 |
|------|------|
| 삭제 대상 | `InventoryStockProjectionEvent.java` |
| 패키지 경로 | `com.eum.saga.inventory` |
| 파일 경로 | `inventoryserver/src/main/java/com/eum/saga/inventory/InventoryStockProjectionEvent.java` |
| 삭제된 디렉터리 | `saga/inventory/`, `saga/` |

**삭제 이유**

이 프로젝트는 **Saga 패턴을 사용하지 않으며**, Outbox Pattern + Debezium CDC 기반 이벤트 체이닝으로 설계되어 있다. `InventoryStockProjectionEvent`는 Saga 패턴 도입 시도 당시 작성된 이벤트 DTO로, 현재 코드베이스 전체에서 단 한 곳도 import하거나 참조하지 않는 순수 데드 코드였다.

**참조 검사 결과**

```
검색 범위: eum_backend 전체 (--include="*.java")
검색어: InventoryStockProjectionEvent, com.eum.saga.inventory
결과: 자기 자신의 선언부(package, class) 2줄 외 참조 없음
```

**관련 이력**

이전 세션에서 Saga 패턴 제거 작업 시 `InventoryOrderSagaHandler.java`(서비스 계층 핸들러)는 삭제했으나, 이벤트 DTO인 `InventoryStockProjectionEvent`가 `com.eum.saga` 하위 별도 패키지에 남아 있어 이번 세션에서 추가 정리함.
