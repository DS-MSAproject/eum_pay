# ProductServer Skills

## Source Of Truth Rules

This file is a derived reference for `productserver`, not the primary source.

- If `SKILLS.md` and code disagree, code wins.
- Do not infer missing fields, endpoints, or behavior from memory.
- Do not use `build/` output as a source of truth.
- Re-read source files before updating any section.
- Update each section only from its declared source files.
- Treat deleted files as deleted facts. Do not preserve old behavior in docs.

Preferred working format for future tasks:

- `Source of truth: <file or directory>`
- `Derived target: productserver/SKILLS.md`
- `Update from source only`

## Source Of Truth Map

- Module identity and dependencies
  - source of truth:
    - `productserver/build.gradle`
    - `productserver/Dockerfile_`
    - `productserver/Jenkinsfile`
- Package structure
  - source of truth:
    - `productserver/src/main/java/com/eum/productserver`
- Resources and seed data
  - source of truth:
    - `productserver/src/main/resources`
    - `productserver/src/main/java/com/eum/productserver/config`
- Entity model
  - source of truth:
    - `productserver/src/main/java/com/eum/productserver/common/BaseTimeEntity.java`
    - `productserver/src/main/java/com/eum/productserver/entity`
- Controller API surface
  - source of truth:
    - `productserver/src/main/java/com/eum/productserver/controller`
- Service behavior
  - source of truth:
    - `productserver/src/main/java/com/eum/productserver/service`
- Repository behavior
  - source of truth:
    - `productserver/src/main/java/com/eum/productserver/repository`
- Current module boundaries
  - source of truth:
    - active code under `productserver/src/main/java`
    - active resources under `productserver/src/main/resources`
    - current module files present in the worktree

## Update Workflow

- Step 1. Identify the exact section to update.
- Step 2. Read the declared source-of-truth files for that section.
- Step 3. Regenerate the section from source, do not patch from memory.
- Step 4. If a fact cannot be proven from source, mark it unknown or omit it.
- Step 5. If multiple files disagree, prefer the runtime code path over comments or stale docs.

## Module Summary

Source of truth:

- `productserver/build.gradle`
- `productserver/src/main/java/com/eum/productserver`

`productserver` is the product domain service in this repository.

- Main responsibilities:
  - product/category 조회 및 상세 응답 제공
  - 상품 옵션 조회
  - 프론트엔드용 상품 상세/목록 응답 조립
  - 체크아웃 검증용 상품 스냅샷 제공
  - 상품 이미지 URL 생성 및 업로드 지원
  - 카테고리/상품 시드 초기화
- Current domain scope:
  - 상품, 카테고리, 상품 이미지, 상품 상세 이미지, 상품 옵션
  - 정기배송(subscription) 기능은 현재 제거된 상태
- Current runtime notes:
  - 재고 수량의 Source of Truth는 `inventoryserver`
  - 상품 목록/판매자 CRUD는 서비스 코드는 존재하지만 컨트롤러 엔드포인트는 주석 처리 상태
  - 예외 응답은 JSON 바디 없이 상태코드만 반환

## Core Dependencies

Source of truth:

- `productserver/build.gradle`

- Runtime / framework
  - Java 17
  - Spring Boot
  - Spring Web
  - Spring Data JPA
  - Spring Validation
  - Spring AOP
  - Spring Actuator
- Service integration
  - Spring Cloud Config
  - Eureka Client
  - OpenFeign
  - Vault Config
- Persistence / query
  - PostgreSQL
  - H2
  - Querydsl JPA
- Shared module
  - `project(':common-resources:s3')`
- Build / packaging
  - Gradle
  - Jib
  - Dockerfile (`Dockerfile_`)
  - Jenkinsfile

## Package Structure

Source of truth:

- `productserver/src/main/java/com/eum/productserver`

Source root: `productserver/src/main/java/com/eum/productserver`

- `client`
  - inter-service client
  - currently includes `InventoryStockClient`
- `common`
  - 공통 예외 처리, 시간 관련 베이스/유틸
  - `GlobalExceptionHandler`
  - `BaseTimeEntity`
  - `TimeUtils`
- `config`
  - 초기 데이터 적재 및 S3 presigner 설정
  - `CategoryDataInitializer`
  - `ProductDataInitializer`
  - `ProductS3PresignerConfig`
- `controller`
  - 외부 API 진입점
  - `CategoryController`
  - `HomeContentController`
  - `ProductCheckoutController`
  - `ProductController`
  - `ProductFrontendController`
  - `ProductOptionController`
  - `ProductSnapshotBootstrapController`
- `dto`
  - 요청/응답/재고 연동용 DTO
  - `request`, `response`, `inventory`
- `entity`
  - JPA 엔티티
  - `Category`
  - `Product`
  - `ProductImage`
  - `ProductDetailImage`
  - `ProductOption`
- `repository`
  - JPA + Querydsl 기반 조회 계층
  - `ProductRepository`, `CategoryRepository`, `ProductOptionRepository`
  - custom repository / impl 포함
- `service`
  - 도메인 로직 및 응답 조립
  - `ProductService`
  - `CategoryService`
  - `ProductOptionService`
  - `ProductCheckoutValidationService`
  - `ProductImageUploadService`
  - `ProductImageUrlService`

## Resources And Seed Data

Source of truth:

- `productserver/src/main/resources`
- `productserver/src/main/java/com/eum/productserver/config`

Resource root: `productserver/src/main/resources`

- `application.yml`
  - 기본 설정 파일
  - 현재 주요 설정:
    - server port `8081`
    - multipart 최대 파일 `10MB`, 최대 요청 `100MB`
    - Config Server / Vault import 활성화
    - S3, Eureka, actuator, tracing, Zipkin endpoint, presigned URL 관련 설정 존재
- `base-logback.xml`, `logback-spring.xml`
  - 로깅 설정
  - 현재 실제 활성 설정은 `logback-spring.xml`
  - `base-logback.xml`은 리소스에 존재하지만 현재 active logback 설정에서 include되지 않음
- `products_seed_data.json`
  - 상품 시드 데이터
  - `optionNames` 로 옵션 기반 상품 시드 정의 가능
  - `options` 로 옵션명과 `extraPrice` 를 명시적으로 정의 가능
  - `options[].extraPrice` 를 생략하면 `0` 으로 처리
  - `detailImageDirectory` 와 image path 기반 이미지 적재 지원
  - 현재 옵션 시드 예시:
    - `오독오독 바삭 10종 골라담기`
    - `오독오독 포켓 3종 대용량 (5개입)`
- `seed/`
  - 추가 시드 리소스 위치로 사용 가능
  - `seed/product-images` 트리를 `ProductDataInitializer`가 직접 읽음

Initial data loading is handled in `config` initializers, not by DDL scripts.

- `CategoryDataInitializer`
  - `CommandLineRunner`
  - `@Order(1)`
  - 루트 카테고리:
    - `ALL`
    - `Snack&Jerky`
    - `Meal`
    - `Bakery`
  - 하위 카테고리와 display order를 보정하며 upsert 성격으로 동작
- `ProductDataInitializer`
  - `ApplicationRunner`
  - `@Order(2)`
  - `" 더미"` suffix의 generated dummy 상품 정리
  - 기존 시드 상품의 옵션/이미지/상세이미지 backfill 수행 가능
  - 메인 이미지 그룹과 상세 이미지 디렉터리를 classpath 기준으로 해석

## Entity Definitions

Source of truth:

- `productserver/src/main/java/com/eum/productserver/common/BaseTimeEntity.java`
- `productserver/src/main/java/com/eum/productserver/entity/Category.java`
- `productserver/src/main/java/com/eum/productserver/entity/Product.java`
- `productserver/src/main/java/com/eum/productserver/entity/ProductImage.java`
- `productserver/src/main/java/com/eum/productserver/entity/ProductDetailImage.java`
- `productserver/src/main/java/com/eum/productserver/entity/ProductOption.java`

This section lists every entity field currently present in code.

Shared mapped superclass:

- `BaseTimeEntity`
  - class annotations:
    - `@MappedSuperclass`
    - `@EntityListeners(AuditingEntityListener.class)`
  - declared fields:
    - `createdDate: LocalDateTime`
      - column: `created_at`
      - annotations:
        - `@CreatedDate`
        - `@Column(name = "created_at", updatable = false)`
    - `modifiedDate: LocalDateTime`
      - column: `modified_at`
      - annotations:
        - `@LastModifiedDate`
        - `@Column(name = "modified_at")`

- `Category`
  - class annotations:
    - `@Entity`
    - `@Table(name = "categories")`
  - superclass:
    - `BaseTimeEntity`
  - all fields:
    - `id: Long`
      - column: `category_id`
      - annotations:
        - `@Id`
        - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
    - `categoryName: String`
      - column: `category_name`
      - annotations:
        - `@Column(name = "category_name", nullable = false, unique = true)`
    - `parent: Category`
      - join column: `parent_id`
      - annotations:
        - `@ManyToOne(fetch = FetchType.LAZY)`
        - `@JoinColumn(name = "parent_id")`
    - `children: List<Category>`
      - annotations:
        - `@OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)`
      - initialized as:
        - `new ArrayList<>()`
    - `products: List<Product>`
      - annotations:
        - `@OneToMany(mappedBy = "category")`
      - initialized as:
        - `new ArrayList<>()`
    - `displayOrder: Integer`
      - no explicit column annotation
  - inherited fields from `BaseTimeEntity`:
    - `createdDate: LocalDateTime`
    - `modifiedDate: LocalDateTime`
  - methods affecting fields:
    - `updateCategoryName(String categoryName)`
    - `updateDisplayOrder(Integer displayOrder)`
    - `setParent(Category parent)`

- `Product`
  - class annotations:
    - `@Entity`
    - `@Table(name = "Products")`
  - superclass:
    - `BaseTimeEntity`
  - enums declared inside class:
    - `ProductStatus`
      - values:
        - `판매중`
        - `판매중지`
  - all fields:
    - `productId: Long`
      - column: `product_id`
      - annotations:
        - `@Id`
        - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
    - `productName: String`
      - annotations:
        - `@Column(nullable = false)`
    - `content: String`
      - annotations:
        - `@Column(columnDefinition = "TEXT")`
    - `imageUrl: String`
      - annotations:
        - `@Column(columnDefinition = "TEXT")`
    - `productUrl: String`
      - annotations:
        - `@Column(length = 1000)`
    - `brandName: String`
      - no explicit column annotation
    - `brandId: Long`
      - no explicit column annotation
    - `price: Long`
      - annotations:
        - `@Column(nullable = false)`
    - `status: ProductStatus`
      - annotations:
        - `@Enumerated(EnumType.STRING)`
    - `salesCount: Long`
      - annotations:
        - `@Builder.Default`
      - default:
        - `0L`
    - `tags: String`
      - no explicit column annotation
    - `category: Category`
      - join column: `category_id`
      - annotations:
        - `@ManyToOne(fetch = FetchType.LAZY)`
        - `@JoinColumn(name = "category_id")`
    - `options: List<ProductOption>`
      - annotations:
        - `@Builder.Default`
        - `@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)`
      - initialized as:
        - `new ArrayList<>()`
    - `images: List<ProductImage>`
      - annotations:
        - `@Builder.Default`
        - `@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)`
      - initialized as:
        - `new ArrayList<>()`
    - `detailImages: List<ProductDetailImage>`
      - annotations:
        - `@Builder.Default`
        - `@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)`
      - initialized as:
        - `new ArrayList<>()`
    - `salesRank: Integer`
      - no explicit column annotation
    - `keywords: String`
      - no explicit column annotation
    - `deliveryFee: Long`
      - annotations:
        - `@Builder.Default`
      - default:
        - `0L`
    - `deliveryMethod: String`
      - annotations:
        - `@Builder.Default`
      - default:
        - `"일반택배"`
  - inherited fields from `BaseTimeEntity`:
    - `createdDate: LocalDateTime`
    - `modifiedDate: LocalDateTime`
  - methods affecting fields:
    - `updateSalesRank(Integer rank)`
    - `increaseSalesCount(int quantity)`
    - `isActiveForProjection()`
    - `addImage(ProductImage productImage)`
    - `addDetailImage(ProductDetailImage productDetailImage)`
    - `addOption(ProductOption productOption)`
    - `updateProduct(Category category, ProductUpdateDto dto)`

- `ProductImage`
  - class annotations:
    - `@Entity`
    - `@Table(name = "product_images")`
  - all fields:
    - `id: Long`
      - annotations:
        - `@Id`
        - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
    - `imageUrl: String`
      - annotations:
        - `@Column(nullable = false, columnDefinition = "TEXT")`
    - `imageKey: String`
      - annotations:
        - `@Column(nullable = false, length = 1000)`
    - `isMain: boolean`
      - no explicit column annotation
    - `product: Product`
      - join column: `product_id`
      - annotations:
        - `@ManyToOne(fetch = FetchType.LAZY)`
        - `@JoinColumn(name = "product_id")`
  - methods affecting fields:
    - `setProduct(Product product)`

- `ProductDetailImage`
  - class annotations:
    - `@Entity`
    - `@Table(name = "product_detail_images")`
  - all fields:
    - `id: Long`
      - column: `product_detail_image_id`
      - annotations:
        - `@Id`
        - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
        - `@Column(name = "product_detail_image_id")`
    - `imageUrl: String`
      - annotations:
        - `@Column(nullable = false, columnDefinition = "TEXT")`
    - `imageKey: String`
      - annotations:
        - `@Column(nullable = false, length = 1000)`
    - `displayOrder: Integer`
      - annotations:
        - `@Column(nullable = false)`
    - `product: Product`
      - join column: `product_id`
      - annotations:
        - `@ManyToOne(fetch = FetchType.LAZY)`
        - `@JoinColumn(name = "product_id")`
  - methods affecting fields:
    - `setProduct(Product product)`

- `ProductOption`
  - class annotations:
    - `@Entity`
    - `@Table(name = "product_options")`
  - superclass:
    - `BaseTimeEntity`
  - all fields:
    - `id: Long`
      - column: `option_id`
      - annotations:
        - `@Id`
        - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
        - `@Column(name = "option_id")`
    - `optionName: String`
      - annotations:
        - `@Column(nullable = true)`
        - `@Nullable`
    - `extraPrice: Long`
      - annotations:
        - `@Builder.Default`
      - default:
        - `0L`
    - `product: Product`
      - join column: `product_id`
      - annotations:
        - `@ManyToOne(fetch = FetchType.LAZY)`
        - `@JoinColumn(name = "product_id")`
  - inherited fields from `BaseTimeEntity`:
    - `createdDate: LocalDateTime`
    - `modifiedDate: LocalDateTime`
  - methods affecting fields:
    - `updateOption(String optionName, Long extraPrice)`
    - `setProduct(Product product)`

## Controller Endpoint Definitions

Source of truth:

- `productserver/src/main/java/com/eum/productserver/controller/CategoryController.java`
- `productserver/src/main/java/com/eum/productserver/controller/HomeContentController.java`
- `productserver/src/main/java/com/eum/productserver/controller/ProductCheckoutController.java`
- `productserver/src/main/java/com/eum/productserver/controller/ProductController.java`
- `productserver/src/main/java/com/eum/productserver/controller/ProductFrontendController.java`
- `productserver/src/main/java/com/eum/productserver/controller/ProductOptionController.java`
- `productserver/src/main/java/com/eum/productserver/controller/ProductSnapshotBootstrapController.java`

Active endpoints:

- `CategoryController`
  - base path: `/product/categories`
  - `GET /product/categories`
    - method: `findAll()`
    - 목적: 전체 카테고리를 대분류/소분류 트리 구조로 조회
    - request:
      - path variable 없음
      - query parameter 없음
      - body 없음
    - response:
      - `200 OK`
      - `List<ResCategoryListDto>`
    - service call:
      - `categoryService.findAll()`

- `ProductController`
  - base path: `/product`
  - `GET /product/{productId}`
    - method: `getProductDetail(Long productId)`
    - 목적: 단일 상품 상세 조회
    - request:
      - path variable: `productId`
      - body 없음
    - response:
      - `200 OK`
      - `ResProductDetail`
    - service call:
      - `productService.findProductDetail(productId)`
    - 비고:
      - 현재 상품 상세 백엔드 API의 기준 경로는 이 엔드포인트

- `ProductOptionController`
  - base path: `/product`
  - `GET /product/{productId}/options`
    - method: `getOptionsByProduct(Long productId)`
    - 목적: 특정 상품의 옵션 목록 조회
    - request:
      - path variable: `productId`
      - body 없음
    - response:
      - `200 OK`
      - `List<ResProductOptionDto>`
    - service call:
      - `productOptionService.findOptionsByProductId(productId)`

- `ProductFrontendController`
  - base path: `/product/frontend`
  - `GET /product/frontend/{productId}`
    - method: `getProductFrontend(Long productId)`
    - 목적: 프론트엔드 화면에 맞춘 상품 응답 조회
    - request:
      - path variable: `productId`
      - body 없음
    - response:
      - `200 OK`
      - `ProductFrontendDto`
    - service call:
      - `productService.getFrontendProduct(productId)`

- `ProductCheckoutController`
  - base path: `/product/checkout`
  - `POST /product/checkout/validate`
    - method: `validate(CheckoutValidationRequest request)`
    - 목적: 주문 직전 상품/옵션/가격/판매상태 검증
    - request:
      - body: `CheckoutValidationRequest`
    - response:
      - `200 OK`
      - `CheckoutValidationResponse`
    - service call:
      - `checkoutValidationService.validate(request)`
    - 비고:
      - 실제 재고 부족 판단은 inventoryserver 예약 단계에서 수행

- `ProductSnapshotBootstrapController`
  - base path: `/internal/products`
  - `GET /internal/products/snapshots`
    - method: `getSnapshots(Long lastProductId, int size)`
    - 목적: 외부 서비스용 상품 스냅샷 배치/부트스트랩 조회
    - request:
      - query parameter: `lastProductId` default `0`
      - query parameter: `size` default `500`
    - response:
      - `200 OK`
      - `ProductSnapshotBootstrapPageDto`
    - service call:
      - `productService.getBootstrapSnapshots(lastProductId, size)`
    - 비고:
      - 공개 사용자 API가 아니라 내부 연동용 경로

Inactive or empty controllers:

- `HomeContentController`
  - 현재 파일이 비어 있음
  - 활성 엔드포인트 없음

- `CategoryController`
  - 주석 처리된 비활성 메서드 존재:
    - `POST /product/categories`
    - `PUT /product/categories/{categoryId}`
    - `DELETE /product/categories/{categoryId}`

- `ProductController`
  - 주석 처리된 비활성 메서드 존재:
    - `GET /product/products`
    - `POST /product/seller`
    - `PUT /product/seller/{productId}`
    - `DELETE /product/seller/{productId}`

## Service Layer Definitions

Source of truth:

- `productserver/src/main/java/com/eum/productserver/service/CategoryService.java`
- `productserver/src/main/java/com/eum/productserver/service/ProductCheckoutValidationService.java`
- `productserver/src/main/java/com/eum/productserver/service/ProductImageUploadService.java`
- `productserver/src/main/java/com/eum/productserver/service/ProductImageUrlService.java`
- `productserver/src/main/java/com/eum/productserver/service/ProductOptionService.java`
- `productserver/src/main/java/com/eum/productserver/service/ProductService.java`

Service overview:

- `CategoryService`
  - 성격:
    - `@Transactional`
    - 카테고리 트리 조회 및 관리 담당
  - 주요 의존성:
    - `CategoryRepository`
    - `ProductRepository`
  - 공개 메서드:
    - `findAll()`
      - 반환: `List<ResCategoryListDto>`
      - 역할: 카테고리 전체를 트리 구조 DTO로 변환
      - 내부 동작:
        - `categoryRepository.findAllWithChildren()` 사용
        - fetch join 기반 조회 결과를 `ResCategoryListDto`로 매핑
    - `save(CategorySaveDto saveDto)`
      - 반환: `ResCategoryDto`
      - 역할: 신규 카테고리 저장
      - 내부 검증:
        - 동일 이름 카테고리 존재 여부 확인
        - 부모 카테고리 존재 여부 확인
      - 내부 동작:
        - 중복 이름이면 기존 카테고리 반환
        - 부모가 있으면 `setParent`로 트리 연결
    - `update(CategoryUpdateDto updateDto)`
      - 반환: `ResCategoryDto`
      - 역할: 카테고리명 및 정렬 순서 수정
    - `delete(Long categoryId)`
      - 반환: 없음
      - 역할: 카테고리 삭제
      - 내부 검증:
        - 하위 카테고리 존재 시 삭제 차단
        - 해당 카테고리 상품 존재 시 삭제 차단

- `ProductCheckoutValidationService`
  - 성격:
    - `@Transactional(readOnly = true)`
    - no-option sentinel: `NO_OPTION_ID = 0L`
  - 주요 의존성:
    - `ProductRepository`
  - 공개 메서드:
    - `validate(CheckoutValidationRequest request)`
      - 반환: `CheckoutValidationResponse`
      - 역할: 주문 직전 상품/옵션/가격 검증 결과를 checkout snapshot으로 변환
      - 내부 검증:
        - request 비어 있음 여부
        - productId 존재 여부
        - quantity > 0 여부
        - 상품 존재 여부
        - 상품 판매 가능 상태 여부
        - optionId가 해당 상품에 속하는지 여부
      - 내부 동작:
        - 상품 기본 가격 + 옵션 추가금 반영
        - `lineTotalPrice` 및 전체 `totalPrice` 계산
      - 비고:
        - 재고 부족 여부는 inventoryserver 예약 단계에서 최종 확인
  - 주요 내부 메서드:
    - `validateItem(...)`
    - `resolveOption(...)`
    - `isNoOptionId(...)`
    - `toExternalOptionId(...)`
    - `resolveProductPrice(...)`

- `ProductImageUploadService`
  - 성격:
    - 상품 이미지 S3 업로드 담당
  - 주요 의존성:
    - `S3Component`
  - 설정값:
    - `cloud.aws.s3.bucket`
    - `cloud.aws.region.static`
  - 업로드 정책:
    - 파일당 최대 10MB
    - 허용 MIME 타입:
      - `image/jpeg`
      - `image/png`
      - `image/gif`
      - `image/webp`
  - 공개 메서드:
    - `uploadAll(List<MultipartFile> files)`
      - 반환: `List<ImageFileSaveDto>`
      - 역할: 여러 이미지를 검증 후 S3 업로드
    - `upload(MultipartFile file)`
      - 반환: `ImageFileSaveDto`
      - 역할: 단일 이미지 업로드
      - 비고:
        - null 또는 empty면 `null` 반환
    - `filterActualFiles(List<MultipartFile> files)`
      - 반환: `List<MultipartFile>`
      - 역할: null/empty 파일 제거
  - 주요 내부 메서드:
    - `validateFiles(...)`
    - `uploadValidated(...)`
    - `buildS3Url(...)`

- `ProductImageUrlService`
  - 성격:
    - 저장된 S3 URL 또는 key를 화면 노출용 URL로 변환
  - 주요 의존성:
    - `S3Presigner`
  - 설정값:
    - `cloud.aws.s3.bucket`
    - `product.image.presigned-url.enabled`
    - `product.image.presigned-url.expiration-minutes`
  - 공개 메서드:
    - `toDisplayUrl(String imageUrlOrKey)`
      - 반환: `String`
      - 역할:
        - 입력이 S3 key 또는 S3 URL이면 presigned URL로 변환 시도
        - 비활성 설정 또는 변환 불가 시 원본 반환
      - 예외 처리:
        - presign 중 런타임 예외 발생 시 원본 반환
  - 주요 내부 메서드:
    - `extractS3Key(...)`
    - `stripLeadingSlash(...)`
    - `decode(...)`

- `ProductOptionService`
  - 성격:
    - `@Transactional(readOnly = true)`
    - 옵션 조회와 옵션별 재고 합성 담당
    - no-option sentinel: `NO_OPTION_ID = 0L`
  - 주요 의존성:
    - `ProductOptionRepository`
    - `InventoryStockClient`
  - 공개 메서드:
    - `findOptionsByProductId(Long productId)`
      - 반환: `List<ResProductOptionDto>`
      - 역할:
        - 특정 상품의 옵션 목록 조회
        - inventoryserver 재고 응답을 옵션 DTO에 합성
    - `findOptionById(Long optionId)`
      - 반환: `ResProductOptionDto`
      - 역할: 옵션 단건 조회
  - 주요 내부 메서드:
    - `fetchOptionStocks(Long productId)`
      - inventoryserver 재고 목록 조회 후 `optionId -> ProductStockInfo` 맵으로 변환
      - `optionId == null` 또는 `optionId == 0` 응답은 옵션별 재고 맵에서 제외
    - `isNoOptionId(...)`

- `ProductService`
  - 성격:
    - `@Transactional`
    - productserver 핵심 도메인 서비스
  - 주요 의존성:
    - `ProductRepository`
    - `CategoryRepository`
    - `ProductOptionRepository`
    - `InventoryStockClient`
    - `ProductImageUploadService`
    - `ProductImageUrlService`
  - 주요 상수:
    - `NO_OPTION_ID = 0`
    - `PRODUCT_URL_PREFIX = https://www.eum.com/product/`
    - `MAX_PRODUCT_IMAGE_COUNT = 20`
  - 공개 메서드:
    - `findAll(Long categoryId, Pageable pageable)`
      - 반환: `Page<ResProductListDto>`
      - 역할: 카테고리 조건 기반 상품 목록 조회
      - 내부 동작:
        - 상품 페이지 조회
        - inventoryserver 재고 조회
        - 상품별 재고 정보와 이미지 URL 변환을 합쳐 DTO 생성
      - 비고:
        - 현재 컨트롤러 엔드포인트는 주석 처리되어 있지만 서비스 메서드는 존재
    - `findProductDetail(Long productId)`
      - 반환: `ResProductDetail`
      - 역할: 상품 상세 조회
      - 내부 동작:
        - 상품 단건 조회
        - inventoryserver 재고 조회
        - 상품 재고와 옵션 재고를 각각 분리 계산
        - 상세 이미지 URL을 화면 노출용 URL로 변환
    - `getFrontendProduct(Long productId)`
      - 반환: `ProductFrontendDto`
      - 역할: 프론트 화면 전용 단순 상품 응답 구성
    - `getBootstrapSnapshots(Long lastProductId, int size)`
      - 반환: `ProductSnapshotBootstrapPageDto`
      - 역할: 내부 서비스 부트스트랩용 상품 스냅샷 페이지 조회
      - 내부 동작:
        - cursor 기반 `productId > lastProductId` 조회
        - size는 1~500 범위로 보정
    - `save(ProductSaveRequest request)`
    - `save(ProductSaveRequest request, List<MultipartFile> imageFiles)`
    - `save(ProductSaveRequest request, List<MultipartFile> imageFiles, List<MultipartFile> detailImageFiles)`
      - 반환: `ResProductSaveDto`
      - 역할: 상품 등록
      - 내부 검증:
        - 카테고리 존재 여부 확인
        - 이미지 개수 제한 확인
        - 이미지 URL 필수값 확인
      - 내부 동작:
        - 요청 DTO 이미지 + multipart 업로드 결과 병합
        - legacy `imageUrl`만 온 경우 대표 이미지로 보정
        - 상품 저장 후 `productUrl` 생성 및 flush
    - `addMissingSeedOptionsAndDetailImages(Long productId, ProductSaveDto seedDto, List<MultipartFile> imageFiles, List<MultipartFile> detailImageFiles)`
      - 반환: `boolean`
      - 역할:
        - 시드 데이터 기준으로 누락된 옵션/이미지/상세이미지를 보완
    - `update(ProductUpdateRequest request)`
    - `update(ProductUpdateRequest request, List<MultipartFile> imageFiles)`
    - `update(ProductUpdateRequest request, List<MultipartFile> imageFiles, List<MultipartFile> detailImageFiles)`
      - 반환: `ResProductUpdateDto`
      - 역할: 상품 수정
      - 내부 검증:
        - 대상 상품 존재 여부 확인
        - 대상 카테고리 존재 여부 확인
        - 이미지 개수 제한 확인
        - optionId 기반 옵션 수정만 허용
    - `deleteProduct(Long id, Long sellerId)`
      - 반환: 없음
      - 역할: 상품 삭제
      - 비고:
        - 현재 `sellerId`는 실제 검증에 사용되지 않음
  - 주요 내부 메서드:
    - 재고 처리:
      - `fetchInventoryStocks(...)`
      - `toProductStocks(...)`
      - `toOptionStocks(...)`
      - `isNoOptionId(...)`
      - `defaultQuantity(...)`
      - `defaultStatus(...)`
      - `resolveStockStatus(...)`
    - 상세 응답 조립:
      - `resolveDetailOptions(...)`
      - `resolveFrontendImageUrl(...)`
    - 저장/수정 이미지 처리:
      - `resolveImages(...)`
      - `resolveDetailImages(...)`
      - `validateImageLimit(...)`
      - `hasImages(...)`
      - `addLegacyImageUrlIfOnlyImage(...)`
      - `addImages(...)`
      - `addDetailImages(...)`
      - `resolveImageKey(...)`
    - 옵션 갱신:
      - `applyOptionUpdates(...)`
      - `syncSeedOptions(...)`
      - `hasSameOptions(...)`
    - 기타:
      - `generateProductUrl(...)`

## Repository Layer Definitions

Source of truth:

- `productserver/src/main/java/com/eum/productserver/repository/CategoryRepository.java`
- `productserver/src/main/java/com/eum/productserver/repository/CategoryRepositoryCustom.java`
- `productserver/src/main/java/com/eum/productserver/repository/CategoryRepositoryImpl.java`
- `productserver/src/main/java/com/eum/productserver/repository/ProductOptionRepository.java`
- `productserver/src/main/java/com/eum/productserver/repository/ProductRepository.java`
- `productserver/src/main/java/com/eum/productserver/repository/ProductRepositoryCustom.java`
- `productserver/src/main/java/com/eum/productserver/repository/ProductRepositoryImpl.java`

Repository overview:

- `CategoryRepository`
  - 타입:
    - `JpaRepository<Category, Long>`
    - `CategoryRepositoryCustom`
  - 역할:
    - 카테고리 기본 CRUD와 트리 조회 지원
  - 선언 메서드:
    - `findByParentIsNullOrderByDisplayOrderAsc()`
    - `findByParentIdOrderByDisplayOrderAsc(Long parentId)`
    - `existsByCategoryName(String categoryName)`
    - `findByCategoryName(String categoryName)`
    - `findAllWithChildren()`
      - `@Query` + `LEFT JOIN FETCH c.children`
      - 최상위 카테고리와 자식 카테고리를 한 번에 조회

- `CategoryRepositoryCustom`
  - 역할:
    - 카테고리 커스텀 조회 계약 정의
  - 선언 메서드:
    - `findAllWithProducts()`

- `CategoryRepositoryImpl`
  - 역할:
    - `CategoryRepositoryCustom` Querydsl 구현체
  - 주요 의존성:
    - `JPAQueryFactory`
  - 구현 메서드:
    - `findAllWithProducts()`
      - `category.parent` fetch join
      - `category.products` fetch join
      - `distinct()` 적용

- `ProductOptionRepository`
  - 타입:
    - `JpaRepository<ProductOption, Long>`
  - 역할:
    - 상품 옵션 기본 조회
  - 선언 메서드:
    - `findByProduct_ProductId(Long productId)`
    - `findByProduct_ProductIdAndOptionName(Long productId, String optionName)`

- `ProductRepository`
  - 타입:
    - `JpaRepository<Product, Long>`
    - `ProductRepositoryCustom`
  - 역할:
    - 상품 기본 CRUD, 존재 여부 확인, 보조 조회 지원
  - 선언 메서드:
    - `existsByCategoryId(Long categoryId)`
    - `existsByProductUrl(String productUrl)`
    - `existsByProductNameAndCategory_Id(String productName, Long categoryId)`
    - `findByProductNameAndCategory_Id(String productName, Long categoryId)`
    - `findByBrandIdOrderByCreatedDateDesc(Long brandId)`
    - `findByStatusNot(Product.ProductStatus status)`
    - `findByProductIdGreaterThanOrderByProductIdAsc(Long lastProductId, Pageable pageable)`

- `ProductRepositoryCustom`
  - 역할:
    - 상품 Querydsl 조회 계약 정의
  - 선언 메서드:
    - `findByCategoryId(Long categoryId, Pageable pageable)`
    - `findTop6ByOrderBySalesCountDesc()`

- `ProductRepositoryImpl`
  - 역할:
    - `ProductRepositoryCustom` Querydsl 구현체
  - 주요 의존성:
    - `JPAQueryFactory`
  - 구현 메서드:
    - `findByCategoryId(Long categoryId, Pageable pageable)`
      - `product.category` fetch join
      - `createdDate desc` 정렬
      - 현재 카테고리 또는 부모 카테고리 일치 조건 사용
    - `findTop6ByOrderBySalesCountDesc()`
      - `product.images` fetch join
      - `productImage.isMain == true`
      - `salesCount desc`
      - `limit 6`
  - 주요 내부 메서드:
    - `categoryIdEq(Long categoryId)`

## Applied Skills And Patterns

Source of truth:

- `productserver/build.gradle`
- `productserver/src/main/java/com/eum/productserver/ProductServerApplication.java`
- `productserver/src/main/java/com/eum/productserver/client`
- `productserver/src/main/java/com/eum/productserver/service`

- Layered Spring MVC architecture
  - controller -> service -> repository
- JPA + Querydsl
  - 기본 CRUD와 커스텀 조회를 함께 사용
- Feign-based service call
  - inventory 관련 재고 정보 연동
- Config / Discovery / Secret externalization
  - Config Server, Eureka, Vault 사용
- S3 integration
  - 공용 `common-resources:s3` 모듈 재사용
- Seed-based bootstrap
  - JSON 기반 상품 초기 적재

## Operational Notes

Source of truth:

- `productserver/build.gradle`
- `productserver/src/main/java/com/eum/productserver/ProductServerApplication.java`
- `productserver/src/test/java`

- Main application class:
  - `com.eum.productserver.ProductServerApplication`
- Enabled features:
  - `@EnableJpaAuditing`
  - `@EnableFeignClients`
  - `@RefreshScope`
  - `JPAQueryFactory` bean 등록
- Container image:
  - Jib builds `dseum/productserver`
- Test status:
  - 현재 `src/test/java` 기준 테스트 코드는 없음

## Current Boundaries

Source of truth:

- current code under `productserver/src/main/java`
- current resources under `productserver/src/main/resources`
- current files present in `productserver`

- 재고 저장, 재고 예약, 결제 오케스트레이션은 이 모듈의 책임이 아님
- 검색/목록/판매자 CRUD 관련 서비스 코드는 남아 있지만 공개 컨트롤러 경로는 현재 주석 처리 상태
- 상품 URL, 상세 응답 구조, 프론트 라우팅은 이 모듈의 API 계약과 프론트 구현이 함께 맞아야 정상 동작함
