# Review API Spec (Review Server Only)

이 문서는 `reviewserver`가 직접 제공하는 API만 정리한 프론트엔드용 명세입니다.

## 1) 라우팅 요약 (Gateway 기준)

- `GET /api/v1/reviews/{publicId}` -> `reviewserver /reviews/{publicId}` (리뷰 상세 조회)
- `POST /api/v1/reviews` -> `reviewserver /reviews` (리뷰 작성)
- `PUT /api/v1/reviews/{publicId}` -> `reviewserver /reviews/{publicId}` (리뷰 수정)
- `DELETE /api/v1/reviews/{publicId}` -> `reviewserver /reviews/{publicId}` (리뷰 삭제)

## 2) API 상세

헤더 규칙:
- `GET /api/v1/reviews/{publicId}`: 추가 헤더 없음
- `POST /api/v1/reviews`: `X-User-Id` 필수
- `PUT /api/v1/reviews/{publicId}`: `X-User-Id` 필수
- `DELETE /api/v1/reviews/{publicId}`: `X-User-Id` 필수

### 2.1 리뷰 상세 조회

- Method: `GET`
- URL: `/api/v1/reviews/{publicId}`
- Path Params
  - `publicId` (UUID)
- Query Params
  - `isInterested` (Boolean, optional): `true`일 때 likeCount 증가

응답 예시:

```json
{
  "status": "success",
  "data": {
    "publicId": "018f90a3-b4de-7e3c-bf8f-a3f4f2191abc",
    "reviewMedias": [
      {
        "url": "https://.../review/1.jpg",
        "mediaType": "IMAGE"
      },
      {
        "url": "https://.../review/2.mp4",
        "mediaType": "VIDEO"
      }
    ],
    "mediaType": "VIDEO",
    "likeCount": 34,
    "writerName": "user-45",
    "star": 5,
    "preferenceScore": 5,
    "repurchaseScore": 4,
    "freshnessScore": 5,
    "content": "아주 만족합니다.",
    "createAt": "2026-04-27T10:20:33",
    "reportUrl": "/api/v1/reviews/018f90a3-b4de-7e3c-bf8f-a3f4f2191abc/report"
  }
}
```

### 2.2 리뷰 작성

- Method: `POST`
- URL: `/api/v1/reviews`
- Content-Type: `multipart/form-data`
- Parts
  - `data` (String, required): JSON 문자열
  - `files` (File[], optional): 최대 5개, 개당 50MB 이하, 비디오는 최대 1개

`data` JSON:

```json
{
  "productId": 12,
  "star": 5,
  "preferenceScore": 5,
  "repurchaseScore": 4,
  "freshnessScore": 5,
  "content": "기호성이 좋아요."
}
```

응답 예시:

```json
{
  "status": "success",
  "data": {
    "publicId": "018f90a3-b4de-7e3c-bf8f-a3f4f2191abc",
    "message": "Review has been successfully created.",
    "redirectUrl": "/profile/reviews"
  }
}
```

### 2.3 리뷰 수정

- Method: `PUT`
- URL: `/api/v1/reviews/{publicId}`
- Content-Type: `multipart/form-data`
- Parts
  - `data` (String, required): JSON 문자열
  - `files` (File[], optional): 전달 시 미디어 교체

`data` JSON:

```json
{
  "star": 4,
  "preferenceScore": 4,
  "repurchaseScore": 4,
  "freshnessScore": 5,
  "content": "수정된 리뷰 내용입니다."
}
```

응답 예시:

```json
{
  "status": "success",
  "data": {
    "publicId": "018f90a3-b4de-7e3c-bf8f-a3f4f2191abc",
    "reviewMedias": [],
    "mediaType": "TEXT",
    "likeCount": 34,
    "writerName": "user-45",
    "star": 4,
    "preferenceScore": 4,
    "repurchaseScore": 4,
    "freshnessScore": 5,
    "content": "수정된 리뷰 내용입니다.",
    "createAt": "2026-04-27T10:20:33",
    "reportUrl": "/api/v1/reviews/018f90a3-b4de-7e3c-bf8f-a3f4f2191abc/report"
  }
}
```

### 2.4 리뷰 삭제

- Method: `DELETE`
- URL: `/api/v1/reviews/{publicId}`

응답 예시:

```json
{
  "status": "success",
  "message": "Review has been deleted."
}
```

## 3) 에러 응답

주요 상태코드:
- `400 Bad Request`: 유효성 실패, 잘못된 multipart/data 형식
- `401 Unauthorized`: `X-User-Id` 누락/오류 또는 작성자 권한 불일치
- `404 Not Found`: 리뷰 없음
- `409 Conflict`: 이미 해당 상품에 리뷰 작성함
- `413 Payload Too Large`: 파일 용량 초과(50MB 초과)
