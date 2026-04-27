# Search Server

`searchserver`는 Elasticsearch, Redis, Kafka를 기반으로 상품/리뷰/공지 검색 API를 제공하는 Spring Boot 3 WebFlux 서버입니다.

## 개요

- 서비스명: `dseum-search`
- 기본 포트: `8087`
- 주요 저장소
  - Elasticsearch: 검색 데이터 조회
  - Redis: 인기 검색어 집계
  - Kafka: 상품 이벤트 연동 준비
- 서비스 디스커버리: Eureka Client 사용
- 설정 소스: Config Server, Vault 사용

## 기술 스택

- Java 17
- Spring Boot 3
- Spring WebFlux
- Spring Data Elasticsearch
- Reactive Redis
- Spring Cloud Stream Kafka Binder
- Eureka Client
- Micrometer, Prometheus, Zipkin

## 디렉터리 기준 핵심 구성

- 컨트롤러
  - `src/main/java/com/eum/searchserver/controller/ProductController.java`
  - `src/main/java/com/eum/searchserver/controller/ReviewSearchController.java`
  - `src/main/java/com/eum/searchserver/controller/NoticeSearchController.java`
- 서비스
  - `src/main/java/com/eum/searchserver/service/ProductService.java`
  - `src/main/java/com/eum/searchserver/service/ReviewSearchService.java`
  - `src/main/java/com/eum/searchserver/service/NoticeService.java`
- 인덱스 문서
  - `src/main/java/com/eum/searchserver/domain/ProductDocument.java`
  - `src/main/java/com/eum/searchserver/domain/ReviewSearchDocument.java`
  - `src/main/java/com/eum/searchserver/domain/NoticeDocument.java`
- Elasticsearch 설정/매핑
  - `src/main/resources/static/settings.json`
  - `src/main/resources/static/mappings.json`
  - `src/main/resources/static/mappings-review.json`
  - `src/main/resources/static/mappings1.json`

## 실행 환경

`application.yml` 기준 필수 의존성은 아래와 같습니다.

- Elasticsearch: `http://elasticsearch:9200`
- Redis: `redis:6379`
- Kafka: `kafka:9092`
- Config Server: `http://configserver:8071`
- Eureka Server: `http://eurekaserver:8070/eureka/`
- Zipkin: `http://zipkin-server:9411/api/v2/spans`
- Vault: `http://dseum-vault:8200`

로컬 단독 실행 예시:

```bash
./gradlew :searchserver:bootRun
```

도커 이미지 빌드 예시:

```bash
./gradlew :searchserver:jibDockerBuild
```

## Elasticsearch 인덱스

현재 문서 매핑 기준 인덱스는 아래와 같습니다.

- 상품: `asgard.public.products`
- 리뷰: `asgard.public.reviews`
- 공지: `asgard.public.notices`

자동 초기화 동작:

- 애플리케이션 시작 시 `ReactiveElasticsearchConfig`의 `CommandLineRunner`가 인덱스 상태를 확인합니다.
- 현재 자동 초기화 대상은 `ProductDocument`, `NoticeDocument`입니다.
- `ReviewSearchDocument`는 자동 생성/매핑 갱신 대상에 포함되어 있지 않습니다.

## 공통 응답 형식

목록 조회 API는 공통으로 `SearchPageResponse<T>`를 사용합니다.

```json
{
  "status": "success",
  "totalElements": 100,
  "totalPages": 9,
  "currentPage": 0,
  "size": 12,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false,
  "extra": {},
  "data": []
}
```

참고:

- `isFirst`, `isLast`는 사용 중인 Jackson 설정에 따라 실제 직렬화 키를 한번 확인하는 것을 권장합니다.
- `extra`는 도메인별 부가 데이터를 담는 확장 필드입니다.

## API 명세

### 1. 상품 검색

`GET /api/v1/search/products`

쿼리 파라미터:

- `title`: 상품명 검색어
- `keyword`: 보조 검색어
- `category`: 대카테고리, 기본값 `ALL`
- `subCategory`: 소카테고리
- `sortType`: `최신순`, `판매량순`, `가격 높은순`, `가격 낮은순`
- `page`: 페이지 번호, 기본값 `0`
- `size`: 요청 가능하지만 실제 응답 크기는 서비스 내부에서 `12` 고정

동작:

- `title`이 있으면 `title.keyword` exact match, `title` nori 분석, `title.edge` prefix match를 조합합니다.
- `category`, `subCategory` 필터를 적용합니다.
- 검색어가 존재하고 결과가 1건 이상이면 Redis ZSet `ranking:products`에 검색어 점수를 적재합니다.
- 검색 액션인 경우 응답 `extra.trendingKeywords`에 인기 검색어 목록을 함께 포함합니다.

예시:

```http
GET /api/v1/search/products?title=육포&category=SNACK&sortType=판매량순&page=0
```

주요 응답 데이터 필드:

- `id`
- `imageUrl`
- `productTitle`
- `originalPrice`
- `price`
- `discountRate`
- `discountTag`
- `isNew`
- `productTag`
- `productUrl`
- `category`

### 2. 베스트셀러

`GET /api/v1/search/products/bestseller`

동작:

- Redis 인기 검색어 상위 6개를 조회합니다.
- 해당 키워드와 일치하는 상품을 Elasticsearch에서 재조회합니다.
- 응답에 `salesRank`, `rankTag`를 포함합니다.

주의:

- 현재 베스트셀러 기준은 `ranking:products` 키에 누적된 검색어 기반입니다.
- 즉, 실제 판매량 집계가 아니라 검색량 기반 결과입니다.

### 3. 자동완성

`GET /api/v1/search/products/autocomplete`

쿼리 파라미터:

- `name`: 자동완성 대상 문자열

동작:

- `title.edge`와 `title.keyword`를 사용해 상위 5건을 반환합니다.
- 응답은 배열이며 각 원소는 `id`, `title`을 가집니다.

예시:

```http
GET /api/v1/search/products/autocomplete?name=캥
```

### 4. 인기 검색어

`GET /api/v1/search/products/trending`

동작:

- Redis ZSet `ranking:products` 상위 5개를 반환합니다.
- 각 항목은 `rank`, `keyword`, `score`를 가집니다.

### 5. 유사 상품 추천

`GET /api/v1/search/products/{productId}/similar`

쿼리 파라미터:

- `size`: 반환 개수, 기본값 `3`

동작:

- 기준 상품(`productId`)을 조회합니다.
- 같은 카테고리 상품을 후보로 가져온 뒤 기준 상품을 제외합니다.
- 점수 계산: `카테고리 0.5 + 가격유사도 0.3 + 할인율유사도 0.1 + 최신성유사도 0.1`
- 점수 내림차순으로 상위 N개를 반환합니다.

주요 응답 데이터 필드:

- `id`
- `imageUrl`
- `productTitle`
- `price`
- `discountRate`
- `score`
- `createdAt`
- `productUrl`

### 6. 리뷰 검색

`GET /api/v1/search/reviews`

쿼리 파라미터:

- `productId`: 특정 상품 리뷰만 조회
- `keyword`: 리뷰 내용/작성자 검색어
- `sortType`: `BEST` 또는 그 외값
- `reviewType`: `ALL`, `PHOTO`, `IMAGE`, `VIDEO`, `TEXT`
- `page`: 기본값 `0`
- `size`: 기본값 `5`

동작:

- `product_id` 필터를 지원합니다.
- `keyword`는 `content^3`, `writer_name` 멀티매치 검색입니다.
- `sortType=BEST`면 `like_count DESC` 우선 정렬입니다.
- 그 외에는 `created_at DESC` 정렬입니다.

예시:

```http
GET /api/v1/search/reviews?productId=10&keyword=맛있어요&reviewType=PHOTO&sortType=BEST
```

주요 응답 데이터 필드:

- `reviewId`
- `productId`
- `writerName`
- `star`
- `likeCount`
- `reviewMediaUrl`
- `mediaType`
- `content`
- `createdAt`
- `reviewDetailUrl`

### 7. 공지 검색

`GET /api/v1/search/notices`

쿼리 파라미터:

- `searchRange`: `전체`, `일주일`, `한달`, `세달`
- `searchType`: `제목` 또는 그 외값
- `keyword`: 검색어
- `page`: 기본값 `0`
- `size`: 기본값 `12`

동작:

- 고정 공지(`is_pinned=true`)를 먼저 조회합니다.
- 일반 공지를 페이지 단위로 조회한 뒤, 응답 데이터 앞부분에 고정 공지를 합쳐 반환합니다.
- `searchRange`는 `created_at` 기준 기간 필터입니다.
- 응답 `extra.menuTitle`에는 항상 `NOTICE`가 포함됩니다.

예시:

```http
GET /api/v1/search/notices?searchRange=한달&searchType=제목&keyword=배송&page=0&size=12
```

주요 응답 데이터 필드:

- `id`
- `category`
- `title`
- `isPinned`
- `contentImageUrls`
- `actions`
- `createdAt`

## 운영 포인트

- 검색 로그는 `ProductService`에서 `SEARCH_METRIC` 형식으로 기록합니다.
- Prometheus 노출 경로는 `/actuator/prometheus`입니다.
- Health, Info, Prometheus 엔드포인트가 열려 있습니다.
- Zipkin 샘플링 비율은 현재 `1.0`입니다.

## 확인이 필요한 사항

- `ProductSearchCondition`에 `minPrice`, `maxPrice`, `searchScope` 필드가 있으나 현재 검색 쿼리에는 반영되지 않습니다.
- `ReviewSearchService`는 정렬을 두 번 추가하고 있어 최종 정렬 우선순위 해석을 한번 점검하는 것이 좋습니다.
- `NoticeSearchController`는 서비스가 `NoticeResponse`를 반환하는데 메서드 import에 `NoticeSearchResponse`가 남아 있습니다. 사용하지 않는 import 정리 대상입니다.
