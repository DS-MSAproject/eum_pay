# SearchServer API Spec (Frontend)

이 문서는 `searchserver` 기준 프론트엔드 연동 명세입니다.  
기본적으로 게이트웨이 경로(`/api/v1/...`)를 기준으로 작성했습니다.

## 1) 라우팅 요약 (Gateway 기준)

- `GET /api/v1/search/products`
- `GET /api/v1/search/products/bestseller`
- `GET /api/v1/search/products/home-bestseller`
- `GET /api/v1/search/products/taste-picks`
- `GET /api/v1/search/products/main-banners`
- `GET /api/v1/search/products/{productId}/similar`
- `GET /api/v1/search/products/{productId}/together`
- `GET /api/v1/search/products/autocomplete`
- `GET /api/v1/search/products/trending`
- `GET /api/v1/search/notices`
- `GET /api/v1/search/categories`
- `GET /api/v1/search/brand-story`
- `GET /api/v1/search/brand-story/detail`
- `GET /api/v1/search/navigation`
- `GET /api/v1/reviews` (조회는 searchserver 담당)
- `GET /api/v1/reviews/header` (조회는 searchserver 담당)
- `GET /api/v1/search/reviews/best-photo`

## 2) 공통 응답 포맷

페이지형 응답(`SearchPageResponse<T>`)의 실제 JSON 마샬링 결과는 아래 형태입니다.

- `extra` 필드 설명
  - 도메인별 부가정보를 담는 선택 필드입니다.
  - 값이 `null`이면 JSON 직렬화 시 응답에서 제외됩니다.
  - 엔드포인트마다 키 구성이 다를 수 있습니다.
  - 현재 확인된 주요 예시
    - 상품 검색(`/api/v1/search/products`): `extra.trendingKeywords`
    - 공지 검색(`/api/v1/search/notices`): `extra.menuTitle`

- `extra`가 없을 때 (`null`이면 직렬화에서 제외)

```json
{
  "status": "success",
  "totalElements": 0,
  "totalPages": 0,
  "currentPage": 0,
  "size": 12,
  "isFirst": true,
  "isLast": true,
  "hasNext": false,
  "hasPrevious": false,
  "data": []
}
```

- `extra`가 있을 때

```json
{
  "status": "success",
  "totalElements": 3,
  "totalPages": 1,
  "currentPage": 0,
  "size": 12,
  "isFirst": true,
  "isLast": true,
  "hasNext": false,
  "hasPrevious": false,
  "extra": {
    "menuTitle": "NOTICE"
  },
  "data": []
}
```

## 3) 상품 검색 API

### 3.1 상품 검색

- Method: `GET`
- URL: `/api/v1/search/products`
- Query Params
  - `title` (String, optional)
  - `keyword` (String, optional)
  - `category` (String, optional, default `ALL`)
  - `subCategory` (String, optional)
  - `minPrice` (Long, optional)
  - `maxPrice` (Long, optional)
  - `searchScope` (String, optional)
  - `sortType` (String, optional, default `최신순`)
    - `최신순` | `판매량순` | `가격 높은순` | `가격 낮은순`
  - `page` (Integer, optional, default `0`)
  - `size` (Integer, optional, 입력하더라도 실제 응답 size는 12 고정)

응답 data item(`ProductSearchResponse`):
- `id`, `imageUrl`, `productTitle`
- `originalPrice`, `price`, `discountRate`
- `discountTag`, `isNew`, `productTag`
- `productUrl`, `category`

`title` 또는 `keyword`로 검색한 경우 `extra.trendingKeywords` 포함:

```json
{
  "extra": {
    "trendingKeywords": ["오리", "테린", "치킨"]
  }
}
```

### 3.2 베스트셀러

- `GET /api/v1/search/products/bestseller`
- 응답 data item(`BestsellerProductResponse`):
  - `id`, `imageUrl`, `productTitle`, `price`, `salesRank`, `rankTag`, `productUrl`

### 3.3 홈 베스트셀러

- `GET /api/v1/search/products/home-bestseller?size=3`
- `size` 기본 `3`, 최대 `20`
- 응답 data item(`HomeBestsellerProductResponse`):
  - `rank`, `id`, `imageUrl`, `productTitle`, `price`, `score`, `salesCount`, `createdAt`, `productUrl`

### 3.4 취향저격

- `GET /api/v1/search/products/taste-picks?brandName=오독오독`
- `brandName` 미지정 시 기본 브랜드로 조회
- 응답 data item(`TastePickProductResponse`):
  - `productId`, `imageUrl`, `title`, `price`, `brandName`, `productUrl`

### 3.5 메인 배너

- `GET /api/v1/search/products/main-banners`
- 응답 data item(`MainBannerProductResponse`):
  - `productId`, `imageUrl`, `displayOrder`, `isHero`

### 3.6 유사 상품

- `GET /api/v1/search/products/{productId}/similar?size=3`
- `size` 기본 `3`, 최대 `20`
- 응답 data item(`SimilarProductResponse`):
  - `productId`, `imageUrl`, `title`, `tags`, `price`

### 3.7 함께 구매

- `GET /api/v1/search/products/{productId}/together?size=3`
- `size` 기본 `3`, 최대 `20`
- 응답 data item(`TogetherProductResponse`):
  - `productId`, `imageUrl`, `title`, `tags`, `price`, `options`
  - `options[]`: `optionId`, `optionName`, `extraPrice`, `initialStock`

### 3.8 자동완성

- `GET /api/v1/search/products/autocomplete?name=치`
- 응답(`Flux<AutocompleteResponse>`):

```json
[
  { "id": 11, "title": "치킨테린" },
  { "id": 32, "title": "스위피 덕본 오리뼈 오리목뼈 150g" }
]
```

### 3.9 인기 검색어

- `GET /api/v1/search/products/trending`
- 응답(`Flux<TrendingKeywordResponse>`):

```json
[
  { "rank": 1, "keyword": "테린", "score": 18.0 },
  { "rank": 2, "keyword": "오리", "score": 11.0 }
]
```

## 4) 공지 검색 API

### 4.1 공지 목록/검색

- Method: `GET`
- URL: `/api/v1/search/notices`
- Query Params
  - `searchRange` (String, optional): `일주일` | `한달` | `세달` | `전체`
  - `searchType` (String, optional): `제목` | 기타(제목+내용)
  - `keyword` (String, optional)
  - `page` (Integer, optional, default `0`)
  - `size` (Integer, optional, 무시됨. 일반글 10건 고정)

응답 data item(`NoticeResponse`):
- `id`, `displayNo`, `displayLabel`, `category`, `title`, `isPinned`, `noticeDetailUrl`, `createdAt`

응답 `extra`:

```json
{
  "menuTitle": "NOTICE"
}
```

## 5) 리뷰 조회 API (SearchServer 담당)

### 5.1 리뷰 목록 조회

- Method: `GET`
- URL: `/api/v1/reviews`
- Query Params
  - `productId` (Long, optional)
  - `keyword` (String, optional)
  - `sortType` (String, optional): `BEST` | 그 외 최신순
  - `reviewType` (String, optional): `ALL` | `PHOTO` | `IMAGE` | `VIDEO` | `TEXT`
  - `page` (Integer, optional, default `0`)
  - `size` (Integer, optional, default `3`)

응답 data item(`ReviewSearchResponse`):
- `reviewId`, `productId`, `writerName`, `star`, `likeCount`
- `reviewMediaUrls` (배열, 최대 5개)
- `mediaType`, `content`, `createdAt`, `reviewDetailUrl`

주의:
- `sortType=BEST`면 내부 정책상 `IMAGE` 리뷰만 조회합니다.

### 5.2 리뷰 헤더 통계

- Method: `GET`
- URL: `/api/v1/reviews/header`
- Query Params
  - `productId` (Long, optional)

응답(`ReviewHeaderResponse`):

```json
{
  "avgRating": 4.41,
  "totalCount": 112,
  "ratingDistribution": {
    "5": 62.5,
    "4": 21.43,
    "3": 10.71,
    "2": 3.57,
    "1": 1.79
  }
}
```

### 5.3 베스트 포토리뷰

- Method: `GET`
- URL: `/api/v1/search/reviews/best-photo`
- Query Params
  - 없음 (고정 TOP 5 반환)

응답 data item(`BestPhotoReviewResponse`):
- `reviewId`, `productId`, `productName`
- `writerName`, `star`, `likeCount`
- `reviewMediaUrls` (이미지 중심, 최대 5개 규격 준수)
- `reviewDetailUrl`, `createdAt`

프론트 렌더링 규칙:
- 베스트 포토리뷰 썸네일은 `reviewMediaUrls[0]`만 사용합니다.
- 즉, 여러 URL이 내려와도 대표 이미지는 첫 번째 요소 1장만 노출합니다.

응답 예시:

```json
{
  "status": "success",
  "data": [
    {
      "reviewId": "018f90a3-b4de-7e3c-bf8f-a3f4f2191abc",
      "productId": 5,
      "productName": "스위피 소이밀크 펫두유 1BOX",
      "writerName": "user-45",
      "star": 5,
      "likeCount": 12,
      "reviewMediaUrls": [
        "https://.../review/1.jpg",
        "https://.../review/2.jpg"
      ],
      "reviewDetailUrl": "/reviews/018f90a3-b4de-7e3c-bf8f-a3f4f2191abc",
      "createdAt": "2026-04-28T14:03:22"
    }
  ]
}
```

## 6) 카테고리/브랜드/네비게이션 API

### 6.1 카테고리

- `GET /api/v1/search/categories`
- 응답:

```json
{
  "status": "success",
  "data": [
    {
      "id": "SNACK",
      "label": "간식",
      "subCategories": [
        { "id": 5, "code": "odokodok", "label": "오독오독" }
      ]
    }
  ]
}
```

### 6.2 브랜드 스토리 메인

- `GET /api/v1/search/brand-story`
- 응답:

```json
{
  "status": "success",
  "data": {
    "mainCard": {
      "imageUrl": "https://...",
      "buttonText": "브랜드 스토리",
      "buttonUrl": "/search/brand-story/detail"
    }
  }
}
```

### 6.3 브랜드 스토리 상세

- `GET /api/v1/search/brand-story/detail`
- 응답:

```json
{
  "status": "success",
  "data": [
    { "imageUrl": "https://...", "displayOrder": 1 }
  ]
}
```

### 6.4 네비게이션

- `GET /api/v1/search/navigation`
- 응답(`NavigationMenuResponse`):
  - `status`
  - `data[]`: `key`, `label`, `emoji`, `route`, `api`
  - `api`: `method`, `endpoint`, `query`, `responsePath`

## 7) 페이지네이션 공통응답 미포함 API

아래 API들은 `SearchPageResponse<T>`를 사용하지 않으므로, `totalElements/totalPages/currentPage/size` 등의 페이지네이션 필드를 포함하지 않습니다.

- `GET /api/v1/search/products/autocomplete` (`Flux<AutocompleteResponse>`)
- `GET /api/v1/search/products/trending` (`Flux<TrendingKeywordResponse>`)
- `GET /api/v1/reviews/header` (`ReviewHeaderResponse`)
- `GET /api/v1/search/reviews/best-photo` (`List<BestPhotoReviewResponse>`)
- `GET /api/v1/search/categories` (`Mono<Map<String, Object>>`)
- `GET /api/v1/search/brand-story` (`Mono<Map<String, Object>>`)
- `GET /api/v1/search/brand-story/detail` (`Mono<Map<String, Object>>`)
- `GET /api/v1/search/navigation` (`NavigationMenuResponse`)

## 8) 호환성 특이사항 (기존 DTO 영향 없음)

- 이번 변경은 **기존 필드 변경/삭제 없이 확장 필드만 추가**하는 방식입니다.
- 상품 검색 응답(`ProductSearchResponse`)에 아래 필드가 추가되었습니다.
  - `productInfo` (String, nullable): 추후 상품 텍스트 상세 정보용
  - `content` (String, nullable): 현재 더미 상품 설명 참조용
- 프론트엔드가 기존에 사용하던 필드(`id`, `imageUrl`, `productTitle`, `price` 등)는 그대로 유지됩니다.
- JSON 특성상 프론트에서 사용하지 않는 신규 필드는 무시되므로, **기존 화면/로직에는 영향이 없습니다**.
