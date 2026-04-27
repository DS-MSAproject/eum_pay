# Review Server API

현재 구현 기준 리뷰 서버 API 명세입니다.

## Base URL
- `/api/v1/reviews`

## 공통
- Header
  - `Authorization: Bearer {accessToken}` (필수)
  - 작성/수정/삭제 시 `X-User-Id` (필수)
  - 작성 시 `X-User-Name` (선택)
- 에러 응답
```json
{ "message": "..." }
```

## 1) 리뷰 목록 조회
### `GET /api/v1/reviews`

### Query Parameters
- `productId` (Long, 필수)
- `keyword` (String, 선택)
- `sortType` (String, 선택: `LATEST`/`BEST`, 기본 `LATEST`)
- `reviewType` (String, 선택: `ALL`/`PHOTO`/`VIDEO`/`TEXT`, 기본 `ALL`)
- `page` (Integer, 선택, 기본값: 0)
- `size` (Integer, 선택, 기본값: 5)

### Response 200
```json
{
  "status": "success",
  "reviewHeader": {
    "starAverage": 4.32,
    "totalReviewNumber": 161,
    "ratioStar5": 60.25,
    "ratioStar4": 20.5,
    "ratioStar3": 10.1,
    "ratioStar2": 5.0,
    "ratioStar1": 4.15,
    "preferenceRatio": 72.0,
    "repurchaseRatio": 68.32,
    "freshnessRatio": 70.12
  },
  "reviewBody": [
    {
      "reviewId": 1025,
      "reviewMediaUrl": "review/images/uuid_file.jpg",
      "likeCount": 12,
      "writerName": "홍길동",
      "createdAt": "2026-04-10T15:22:31.123",
      "content": "배송 빠르고 좋아요.",
      "star": 5,
      "mediaType": "IMAGE",
      "reviewDetailUrl": "/api/v1/reviews/1025"
    }
  ],
  "pageInfo": {
    "pageable": {
      "sort": {
        "sorted": true,
        "unsorted": false,
        "empty": false
      },
      "offset": 0,
      "pageNumber": 0,
      "pageSize": 5,
      "paged": true,
      "unpaged": false
    },
    "totalPages": 17,
    "totalElements": 161,
    "last": false,
    "size": 5,
    "number": 0,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "numberOfElements": 5,
    "first": true,
    "empty": false
  }
}
```

## 2) 리뷰 상세 조회(더보기)
### `GET /api/v1/reviews/{reviewId}`

### Path Variables
- `reviewId` (Long, 필수)

### Query Parameters
- `isInterested` (Boolean, 선택)
  - `true`면 `likeCount` 1 증가

### Response 200
```json
{
  "status": "success",
  "data": {
    "reviewId": 1025,
    "reviewMediaUrl": "review/images/uuid_file.jpg",
    "mediaType": "IMAGE",
    "likeCount": 13,
    "writerName": "홍길동",
    "star": 5,
    "preferenceScore": 5,
    "repurchaseScore": 4,
    "freshnessScore": 5,
    "content": "아주 만족해요.",
    "createAt": "2026-04-10T15:22:31.123",
    "reportUrl": "/api/v1/reviews/1025/report"
  }
}
```

## 3) 리뷰 작성
### `POST /api/v1/reviews`
- `Content-Type: multipart/form-data`

### Headers
- `Authorization` (필수)
- `X-User-Id` (필수)
- `X-User-Name` (선택)

### Multipart Body
- `data` (필수)
  - `application/json` 파트 또는 JSON 문자열 텍스트 모두 허용
```json
{
  "productId": 1,
  "star": 5,
  "preferenceScore": 5,
  "repurchaseScore": 5,
  "freshnessScore": 4,
  "content": "만족합니다."
}
```
- `files` (file[], 선택, 파일당 최대 50MB)

### Response 200
```json
{
  "status": "success",
  "data": {
    "reviewId": 1025,
    "message": "Review has been successfully created.",
    "redirectUrl": "/profile/reviews"
  }
}
```

## 4) 리뷰 수정 (본인만)
### `PUT /api/v1/reviews/{reviewId}`
- `Content-Type: multipart/form-data`

### Headers
- `Authorization` (필수)
- `X-User-Id` (필수)

### Path Variables
- `reviewId` (Long, 필수)

### Multipart Body
- `data` (필수)
  - `application/json` 파트 또는 JSON 문자열 텍스트 모두 허용
```json
{
  "star": 4,
  "preferenceScore": 4,
  "repurchaseScore": 4,
  "freshnessScore": 4,
  "content": "수정된 리뷰 내용"
}
```
- `files` (file[], 선택)

### Response 200
```json
{
  "status": "success",
  "data": {
    "reviewId": 1025,
    "reviewMediaUrl": "review/images/new_uuid_file.jpg",
    "mediaType": "IMAGE",
    "likeCount": 13,
    "writerName": "홍길동",
    "star": 4,
    "preferenceScore": 4,
    "repurchaseScore": 4,
    "freshnessScore": 4,
    "content": "수정된 리뷰 내용",
    "createAt": "2026-04-10T15:22:31.123",
    "reportUrl": "/api/v1/reviews/1025/report"
  }
}
```

## 5) 리뷰 삭제 (본인만)
### `DELETE /api/v1/reviews/{reviewId}`

### Headers
- `Authorization` (필수)
- `X-User-Id` (필수)

### Path Variables
- `reviewId` (Long, 필수)

### Response 200
```json
{
  "status": "success",
  "message": "Review has been deleted."
}
```

## 에러 코드
- `400 Bad Request`
  - 예: `content is null`
- `401 Unauthorized`
  - `Invalid token`
- `404 Not Found`
  - `User not found`
  - `Review not found`
- `409 Conflict`
  - `You have already reviewed this product`
- `413 Payload Too Large`
  - `File size exceeds limit (Max 50MB)`

## 비즈니스 규칙
- `star`: 1~5
- `repurchaseScore`: 1~5
- `preferenceScore`: 1~5
- `freshnessScore`: 1~5
- `content`: 필수
- 중복 리뷰 방지: 동일 `productId + writerId`는 1회만 작성 가능
- 수정/삭제는 작성자 본인(`writerId == X-User-Id`)만 가능
