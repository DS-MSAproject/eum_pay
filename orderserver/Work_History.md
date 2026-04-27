# OrderServer 작업 이력

---

## [2026-04-25] OrderItemRequest @Positive → @PositiveOrZero 버그 수정

### 문제 상황

`POST /api/v1/orders/get` 요청 시 400 Bad Request 발생.

**요청 페이로드:**
```json
{
  "user_name": "최원우",
  "receiver_name": "최원우",
  "receiver_phone": "01064822955",
  "receiver_addr": "48038 부산 해운대구 선수촌로 57-22 (반여동) a동 1101호 문 앞에 놓아주세요",
  "items": [
    {"productId": 31, "optionId": 0, "quantity": 1},
    {"productId": 36, "optionId": 71, "quantity": 2}
  ]
}
```

### 원인

`OrderItemRequest.optionId`에 `@Positive` 제약이 적용되어 있었음.
`@Positive`는 `> 0`만 허용하는데, 첫 번째 아이템의 `"optionId": 0`은 **옵션 없음**을 의미하는 유효한 값임.
Bean Validation이 `optionId = 0`을 검증 실패로 처리 → 400 반환.

### 수정 내역

**파일:** `orderserver/src/main/java/com/eum/orderserver/dto/OrderItemRequest.java`

```java
// Before
@JsonProperty("optionId")
@NotNull
@Positive
private Long optionId;

// After
@JsonProperty("optionId")
@NotNull
@PositiveOrZero
private Long optionId;
```

### 비고

| 필드 | 제약 | 이유 |
|------|------|------|
| `productId` | `@Positive` 유지 | 상품 ID는 반드시 > 0 |
| `optionId` | `@PositiveOrZero` 변경 | 0 = 옵션 없음, 유효한 값 |
| `quantity` | `@Positive` 유지 | 수량은 반드시 > 0 |
