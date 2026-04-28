# Auth API Spec (Auth Server Only)

이 문서는 `authserver`가 직접 제공하는 API만 정리한 프론트엔드용 명세입니다.

## 1) 라우팅 요약 (Gateway 기준)

- `POST /api/v1/auth/signup` -> `authserver /auth/signup` (회원가입)
- `POST /api/v1/auth/login` -> `authserver /auth/login` (아이디 로그인)
- `POST /api/v1/auth/refresh` -> `authserver /auth/refresh` (Access Token 재발급)
- `POST /api/v1/auth/logout` -> `authserver /auth/logout` (로그아웃)
- `POST /api/v1/auth/email/send` -> `authserver /auth/email/send` (이메일 인증 코드 발송)
- `POST /api/v1/auth/email/verify` -> `authserver /auth/email/verify` (이메일 인증 코드 검증)
- `GET /api/v1/auth/terms` -> `authserver /auth/terms` (활성 약관 목록 조회)
- `GET /api/v1/users/profile` -> `authserver /users/profile` (마이페이지 메인 조회)
- `GET /api/v1/users/me` -> `authserver /users/me` (회원정보 수정 폼 조회)
- `PUT /api/v1/users/profile` -> `authserver /users/profile` (회원정보 수정)
- `DELETE /api/v1/users` -> `authserver /users` (회원 탈퇴)
- `GET /api/v1/users/addresses` -> `authserver /users/addresses` (배송지 목록 조회)
- `POST /api/v1/users/addresses` -> `authserver /users/addresses` (배송지 등록)
- `PUT /api/v1/users/addresses/{addressId}` -> `authserver /users/addresses/{addressId}` (배송지 수정)
- `DELETE /api/v1/users/addresses/{addressId}` -> `authserver /users/addresses/{addressId}` (배송지 삭제)
- `GET /api/v1/oauth2/authorization/{provider}` -> `authserver /oauth2/authorization/{provider}` (소셜 로그인 시작)
- `GET /.well-known/jwks.json` -> `authserver /.well-known/jwks.json` (JWT 공개키 조회)

## 2) API 상세

헤더 규칙:
- `POST /api/v1/auth/signup`: 추가 헤더 없음
- `POST /api/v1/auth/login`: 추가 헤더 없음
- `POST /api/v1/auth/refresh`: `refreshToken` 쿠키 필요
- `POST /api/v1/auth/logout`: `Authorization: Bearer {accessToken}` 또는 `accessToken` 쿠키 사용
- `POST /api/v1/auth/email/send`: 추가 헤더 없음
- `POST /api/v1/auth/email/verify`: 추가 헤더 없음
- `GET /api/v1/auth/terms`: 추가 헤더 없음
- `GET /api/v1/users/profile`: 로그인 필요
- `GET /api/v1/users/me`: 로그인 필요
- `PUT /api/v1/users/profile`: 로그인 필요
- `DELETE /api/v1/users`: 로그인 필요
- `GET /api/v1/users/addresses`: 로그인 필요
- `POST /api/v1/users/addresses`: 로그인 필요
- `PUT /api/v1/users/addresses/{addressId}`: 로그인 필요
- `DELETE /api/v1/users/addresses/{addressId}`: 로그인 필요

### 2.1 회원가입

- Method: `POST`
- URL: `/api/v1/auth/signup`
- Content-Type: `application/json`
- Body
  - `username` (String, required): 로그인용 아이디, 영문/숫자 4~20자
  - `name` (String, required): 이름, 2~20자
  - `email` (String, required): 이메일
  - `password` (String, required): 8~20자, 영문 대문자/소문자/숫자/특수문자 각각 1개 이상 포함
  - `phoneNumber` (String, required): 휴대폰 번호
  - `termsAgreed` (Object, required): 약관 동의 정보

요청 예시:

```json
{
  "username": "user1234",
  "name": "홍길동",
  "email": "user@example.com",
  "password": "Password1!",
  "phoneNumber": "010-1234-5678",
  "termsAgreed": {
    "service_terms": true,
    "privacy_policy": true,
    "marketing_sms": false
  }
}
```

응답 예시:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

참고:
- 응답 시 `accessToken`, `refreshToken` HttpOnly 쿠키도 함께 내려갑니다.
- 회원가입 전 `POST /api/v1/auth/email/send`, `POST /api/v1/auth/email/verify`로 이메일 인증이 완료되어야 합니다.

### 2.2 아이디 로그인

- Method: `POST`
- URL: `/api/v1/auth/login`
- Content-Type: `application/json`
- Body
  - `username` (String, required): 로그인용 아이디
  - `password` (String, required): 비밀번호

요청 예시:

```json
{
  "username": "user1234",
  "password": "Password1!"
}
```

응답 예시:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

참고:
- 응답 시 `accessToken`, `refreshToken` HttpOnly 쿠키도 함께 내려갑니다.
- 로그인 실패 5회 시 계정이 일시 잠금 처리됩니다.

### 2.3 Access Token 재발급

- Method: `POST`
- URL: `/api/v1/auth/refresh`
- Cookie
  - `refreshToken` (required): 로그인/회원가입 시 발급된 Refresh Token

응답 예시:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

참고:
- Refresh Token rotation 방식입니다.
- 비정상적인 이전 Refresh Token 재사용이 감지되면 세션이 강제 종료됩니다.

### 2.4 로그아웃

- Method: `POST`
- URL: `/api/v1/auth/logout`
- Headers
  - `Authorization` (String, optional): `Bearer {accessToken}`

응답 예시:

```json
{}
```

참고:
- `Authorization` 헤더가 없으면 `accessToken` 쿠키를 기준으로 로그아웃합니다.
- 응답 시 `accessToken`, `refreshToken` 쿠키가 만료됩니다.

### 2.5 이메일 인증 코드 발송

- Method: `POST`
- URL: `/api/v1/auth/email/send`
- Query Params
  - `email` (String, required): 인증 코드를 받을 이메일

요청 예시:

```text
POST /api/v1/auth/email/send?email=user@example.com
```

응답 예시:

```json
{}
```

### 2.6 이메일 인증 코드 검증

- Method: `POST`
- URL: `/api/v1/auth/email/verify`
- Query Params
  - `email` (String, required): 인증 대상 이메일
  - `code` (String, required): 6자리 인증 코드

요청 예시:

```text
POST /api/v1/auth/email/verify?email=user@example.com&code=123456
```

응답 예시:

```json
{}
```

### 2.7 활성 약관 목록 조회

- Method: `GET`
- URL: `/api/v1/auth/terms`

응답 예시:

```json
{
  "status": "success",
  "terms": [
    {
      "id": "service_terms",
      "title": "서비스 이용약관",
      "content": "<p>약관 본문</p>",
      "isRequired": true,
      "version": "1.0",
      "lastUpdated": "2026-04-27T10:20:33"
    },
    {
      "id": "marketing_sms",
      "title": "SMS 마케팅 수신 동의",
      "content": "<p>약관 본문</p>",
      "isRequired": false,
      "version": "1.0",
      "lastUpdated": "2026-04-27T10:20:33"
    }
  ]
}
```

### 2.8 마이페이지 메인 조회

- Method: `GET`
- URL: `/api/v1/users/profile`

응답 예시:

```json
{
  "status": "success",
  "data": {
    "userSummary": {
      "id": 1,
      "name": "홍길동",
      "greetingMessage": "홍길동님 안녕하세요!",
      "membershipLevel": "일반회원"
    },
    "benefits": {
      "points": 0,
      "couponCount": 0,
      "orderTotalCount": 3
    },
    "orderStatusSummary": {
      "recentPeriod": "최근 3개월",
      "mainStatuses": {
        "pendingPayment": 0,
        "preparing": 0,
        "shipping": 0,
        "delivered": 0
      },
      "subStatuses": {
        "cancelled": 0,
        "exchanged": 0,
        "returned": 0
      }
    },
    "activityCounts": {
      "wishlistCount": 0,
      "postCount": 0,
      "regularDeliveryCount": 0
    }
  }
}
```

참고:
- `orderTotalCount`는 orderserver 주문 목록 API의 `totalElements`를 연동한 값입니다.
- 쿠폰 수, 주문 상태 요약, 찜 수, 게시글 수, 정기배송 수는 현재 기본값 `0`입니다.

### 2.9 회원정보 수정 폼 조회

- Method: `GET`
- URL: `/api/v1/users/me`

응답 예시:

```json
{
  "status": "success",
  "data": {
    "userId": "user1234",
    "name": "홍길동",
    "email": "user@example.com",
    "phoneNumber": "010-1234-5678",
    "smsAllowed": false,
    "emailAllowed": false,
    "updatedAt": "2026-04-27T10:20:33"
  }
}
```

### 2.10 회원정보 수정

- Method: `PUT`
- URL: `/api/v1/users/profile`
- Content-Type: `application/json`
- Body
  - `phoneNumber` (String, optional): 변경할 휴대폰 번호
  - `currentPassword` (String, optional): 현재 비밀번호
  - `newPassword` (String, optional): 새 비밀번호
  - `confirmPassword` (String, optional): 새 비밀번호 확인
  - `marketingConsent` (Object, optional): 마케팅 수신 동의

요청 예시:

```json
{
  "phoneNumber": "010-2222-3333",
  "currentPassword": "Password1!",
  "newPassword": "NewPassword1!",
  "confirmPassword": "NewPassword1!",
  "marketingConsent": {
    "smsAllowed": true,
    "emailAllowed": false
  }
}
```

응답 예시:

```json
{
  "status": "success",
  "data": {
    "userId": "user1234",
    "name": "홍길동",
    "email": "user@example.com",
    "phoneNumber": "010-2222-3333",
    "smsAllowed": true,
    "emailAllowed": false,
    "updatedAt": "2026-04-27T10:30:00"
  }
}
```

참고:
- `name`, `email`은 프론트가 보내도 서버에서 수정하지 않습니다.
- 비밀번호 변경은 `currentPassword`, `newPassword`, `confirmPassword`가 함께 맞아야 처리됩니다.

### 2.11 회원 탈퇴

- Method: `DELETE`
- URL: `/api/v1/users`
- Content-Type: `application/json`
- Body
  - `password` (String, optional): 로컬 계정 비밀번호 확인용

요청 예시:

```json
{
  "password": "Password1!"
}
```

응답 예시:

```json
{
  "status": "success",
  "data": {
    "withdrawal_date": "2026-04-27T10:20:33"
  }
}
```

참고:
- 소셜 전용 계정은 비밀번호가 없을 수 있어 비밀번호 확인을 생략할 수 있습니다.

### 2.12 배송지 목록 조회

- Method: `GET`
- URL: `/api/v1/users/addresses`

응답 예시:

```json
{
  "status": "success",
  "data": {
    "totalCount": 1,
    "addresses": [
      {
        "addressId": 10,
        "addressName": "집",
        "isDefault": true,
        "recipientName": "홍길동",
        "postcode": "06236",
        "baseAddress": "서울 강남구 테헤란로 123",
        "detailAddress": "101동 1001호",
        "extraAddress": "역삼동",
        "addressType": "ROAD",
        "phoneNumber": "010-1234-5678",
        "updatedAt": "2026-04-27T10:20:33"
      }
    ]
  }
}
```

### 2.13 배송지 등록

- Method: `POST`
- URL: `/api/v1/users/addresses`
- Content-Type: `application/json`
- Body
  - `addressName` (String, optional): 배송지 별명, 없으면 `미지정`
  - `postcode` (String, required): 5자리 우편번호
  - `baseAddress` (String, required): 기본 주소
  - `detailAddress` (String, optional): 상세 주소
  - `extraAddress` (String, optional): 참고 항목
  - `addressType` (String, optional): `ROAD` 또는 `JIBUN`
  - `isDefault` (Boolean, optional): 기본 배송지 여부

요청 예시:

```json
{
  "addressName": "집",
  "postcode": "06236",
  "baseAddress": "서울 강남구 테헤란로 123",
  "detailAddress": "101동 1001호",
  "extraAddress": "역삼동",
  "addressType": "ROAD",
  "isDefault": true
}
```

응답 예시:

```json
{
  "status": "success",
  "data": {
    "addressId": 10,
    "addressName": "집",
    "isDefault": true,
    "recipientName": "홍길동",
    "postcode": "06236",
    "baseAddress": "서울 강남구 테헤란로 123",
    "detailAddress": "101동 1001호",
    "extraAddress": "역삼동",
    "addressType": "ROAD",
    "phoneNumber": "010-1234-5678",
    "updatedAt": "2026-04-27T10:20:33"
  }
}
```

참고:
- 수령인 이름과 전화번호는 현재 로그인한 회원 정보에서 자동으로 채워집니다.
- 첫 번째 배송지는 자동으로 기본 배송지가 됩니다.

### 2.14 배송지 수정

- Method: `PUT`
- URL: `/api/v1/users/addresses/{addressId}`
- Path Params
  - `addressId` (Long)
- Content-Type: `application/json`

요청 예시:

```json
{
  "addressName": "회사",
  "postcode": "04524",
  "baseAddress": "서울 중구 세종대로 110",
  "detailAddress": "5층",
  "extraAddress": "태평로1가",
  "addressType": "ROAD",
  "isDefault": false
}
```

응답 예시:

```json
{
  "status": "success",
  "data": {
    "addressId": 10,
    "addressName": "회사",
    "isDefault": false,
    "recipientName": "홍길동",
    "postcode": "04524",
    "baseAddress": "서울 중구 세종대로 110",
    "detailAddress": "5층",
    "extraAddress": "태평로1가",
    "addressType": "ROAD",
    "phoneNumber": "010-1234-5678",
    "updatedAt": "2026-04-27T10:30:00"
  }
}
```

### 2.15 배송지 삭제

- Method: `DELETE`
- URL: `/api/v1/users/addresses/{addressId}`
- Path Params
  - `addressId` (Long)

응답 예시:

```json
{
  "status": "success",
  "data": {
    "deleted_address_id": 10
  }
}
```

### 2.16 소셜 로그인 시작

- Method: `GET`
- URL: `/api/v1/oauth2/authorization/{provider}`
- Path Params
  - `provider` (String): `google`, `naver`, `kakao`

요청 예시:

```text
GET /api/v1/oauth2/authorization/google
```

응답:
- 브라우저가 소셜 제공자 로그인 페이지로 redirect 됩니다.
- 로그인 성공 시 프론트엔드 `/` 경로로 redirect 됩니다.
- 로그인 실패 시 프론트엔드 `/login?error={message}` 경로로 redirect 됩니다.

### 2.17 JWT 공개키 조회

- Method: `GET`
- URL: `/.well-known/jwks.json`

응답 예시:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "gopang-auth-key-1",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

참고:
- 프론트 직접 호출용이라기보다는 Gateway가 JWT 검증 공개키를 캐싱할 때 사용하는 엔드포인트입니다.

## 3) 에러 응답

주요 상태코드:
- `400 Bad Request`: 유효성 실패, 잘못된 JSON 형식, 중복 아이디/이메일/전화번호, 이메일 미인증
- `401 Unauthorized`: 로그인 필요, Refresh Token 누락/만료/불일치
- `423 Locked`: 로그인 실패 횟수 초과로 계정 일시 잠금
- `415 Unsupported Media Type`: 지원하지 않는 Content-Type

에러 응답 예시:

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "입력값이 올바르지 않습니다.",
  "errors": {
    "username": "아이디는 4자 이상 20자 이하여야 합니다."
  }
}
```
