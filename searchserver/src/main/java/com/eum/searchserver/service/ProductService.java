package com.eum.searchserver.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.eum.searchserver.domain.ProductBannerDocument;
import com.eum.searchserver.domain.ProductDocument;
import com.eum.searchserver.domain.ProductOptionDocument;
import com.eum.searchserver.dto.request.ProductSearchCondition;
import com.eum.searchserver.dto.response.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ReactiveElasticsearchOperations operations;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String RANKING_KEY = "ranking:products";
    private static final int PAGE_SIZE = 12; // 💡 명세대로 12개 고정
    private static final int HOME_BESTSELLER_DEFAULT_SIZE = 3;
    private static final int HOME_BESTSELLER_MAX_SIZE = 20;
    private static final int HOME_BESTSELLER_CANDIDATE_SIZE = 200;
    private static final int MAIN_BANNER_HERO_SIZE = 3;
    private static final int MAIN_BANNER_CANDIDATE_SIZE = 200;
    private static final int TASTE_PICK_SIZE = 3;
    private static final int TASTE_PICK_CANDIDATE_SIZE = 300;
    private static final int SIMILAR_DEFAULT_SIZE = 3;
    private static final int SIMILAR_MAX_SIZE = 20;
    private static final int SIMILAR_CANDIDATE_SIZE = 200;
    private static final int TOGETHER_DEFAULT_SIZE = 3;
    private static final int TOGETHER_MAX_SIZE = 20;
    private static final int TOGETHER_CANDIDATE_SIZE = 200;
    private static final int SEARCH_CANDIDATE_SIZE = 500;
    private static final double RECENCY_WEIGHT = 1.0;
    private static final double SIMILAR_CATEGORY_WEIGHT = 0.5;
    private static final double SIMILAR_PRICE_WEIGHT = 0.3;
    private static final double SIMILAR_DISCOUNT_WEIGHT = 0.1;
    private static final double SIMILAR_RECENCY_WEIGHT = 0.1;
    private static final double TOGETHER_CATEGORY_WEIGHT = 0.6;
    private static final double TOGETHER_PRICE_WEIGHT = 0.25;
    private static final double TOGETHER_RECENCY_WEIGHT = 0.15;
    private static final long RECENCY_WINDOW_DAYS = 30;
    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<Long> SNACK_JERKY_CATEGORY_IDS = List.of(5L, 6L, 7L, 8L);
    private static final List<Long> MEAL_CATEGORY_IDS = List.of(9L, 10L, 11L);
    private static final List<Long> BAKERY_CATEGORY_IDS = List.of(4L);
    private static final Map<String, Long> SUB_CATEGORY_CODE_TO_ID = Map.of(
            "odokodok", 5L,
            "ugle_jerky", 6L,
            "ugle_milk_gum", 7L,
            "long_lasting_snack", 8L,
            "sweepy_terrine", 9L,
            "ugle_steam", 10L,
            "sweepy_greenbean", 11L
    );
    private static final List<String> TASTE_PICK_BRANDS = List.of("오독오독", "어글어글", "스위피");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [최종] 상품 고급 검색 & 스토어 조회
     * 정렬 필터(최신순, 판매량순, 가격순) 및 인기 검색어 적재 포함
     */
    public Mono<SearchPageResponse<ProductSearchResponse>> searchAdvanced(ProductSearchCondition condition) {
        int page = Math.max(condition.page(), 0);
        Pageable pageable = PageRequest.of(0, SEARCH_CANDIDATE_SIZE);
        String searchKeyword = resolveSearchKeyword(condition);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.matchAll(ma -> ma));
                    return b;
                }))
                .withPageable(pageable)
                // 💡 3. 정렬 필터 적용 (최신순 디폴트)
                .withSort(s -> {
                    String sortType = (condition.sortType() != null) ? condition.sortType() : "최신순";

                    return switch (sortType) {
                        case "판매량순" -> s.field(f -> f.field("salesCount").order(SortOrder.Desc));
                        case "가격 높은순" -> s.field(f -> f.field("price").order(SortOrder.Desc));
                        case "가격 낮은순" -> s.field(f -> f.field("price").order(SortOrder.Asc));
                        default -> s.field(f -> f.field("created_at").order(SortOrder.Desc));
                    };
                })
                .build();

        return operations.search(query, ProductDocument.class)
                .map(hit -> hit.getContent())
                .collectList()
                .flatMap(allDocs -> {
                    List<ProductDocument> filtered = allDocs.stream()
                            .filter(doc -> matchesSearchKeyword(doc, searchKeyword))
                            .filter(doc -> matchesCategory(doc, condition.category()))
                            .filter(doc -> matchesSubCategory(doc, condition.subCategory()))
                            .toList();

                    int from = Math.min(page * PAGE_SIZE, filtered.size());
                    int to = Math.min(from + PAGE_SIZE, filtered.size());
                    List<ProductSearchResponse> data = filtered.subList(from, to).stream()
                            .map(doc -> mapToProductResponse(doc, determineRankTag(doc)))
                            .toList();

                    long total = filtered.size();

                    if (hasText(searchKeyword)) {
                        String cleanTitle = searchKeyword.trim();
                        try {
                            var logData = java.util.Map.of(
                                    "event_type", "TITLE_SEARCH_ACTION",
                                    "search_title", cleanTitle,
                                    "result_count", total,
                                    "category_filter", (condition.category() != null ? condition.category() : "ALL"),
                                    "timestamp", java.time.Instant.now().toString()
                            );
                            log.info("SEARCH_METRIC: {}", objectMapper.writeValueAsString(logData));
                        } catch (Exception e) {
                            log.warn("### [로그 에러] 통계 로그 작성 실패: {} ###", e.getMessage());
                        }

                        if (total > 0) {
                            return redisTemplate.opsForZSet()
                                    .incrementScore(RANKING_KEY, cleanTitle, 1.0)
                                    .thenReturn(SearchPageResponse.of(data, total, page, PAGE_SIZE));
                        }
                    }

                    return Mono.just(SearchPageResponse.of(data, total, page, PAGE_SIZE));
                });
    }

    /**
     * 💡 자동완성 (검색창 타이핑 시 상위 5개 제안)
     */
    public Flux<AutocompleteResponse> getAutocompleteSuggestions(String name) {
        if (!hasText(name)) return Flux.empty();

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .should(s -> s.matchPhrasePrefix(m -> m.field("product_name").query(name).boost(8f)))
                        .should(s -> s.match(m -> m.field("product_name").query(name).boost(6f)))
                        .should(s -> s.match(m -> m.field("keywords").query(name).boost(4f)))
                        .should(s -> s.wildcard(w -> w.field("product_name").value("*" + name + "*").boost(2f)))
                        .minimumShouldMatch("1")
                ))
                .withPageable(PageRequest.of(0, 5))
                .build();

        return operations.search(query, ProductDocument.class)
                .map(hit -> new AutocompleteResponse(hit.getContent().getId(), resolveProductTitle(hit.getContent())))
                .filter(res -> hasText(res.title()))
                .distinct();
    }

    /**
     * 💡 실시간 인기 검색어 (돋보기 아이콘 클릭 시 TOP 5)
     */
    public Flux<TrendingKeywordResponse> getTrendingKeywords() {
        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(RANKING_KEY, Range.closed(0L, 4L))
                .index()
                .map(tuple -> TrendingKeywordResponse.of(
                        tuple.getT1().intValue() + 1, // 순위
                        tuple.getT2().getValue(),     // 키워드
                        tuple.getT2().getScore()      // 점수(선택)
                ));
    }

    private String determineRankTag(ProductDocument doc) {
        if (doc.getSalesRank() == null) return "";

        // 💡 null-safe한 비교를 위해 switch나 Objects.equals 사용
        return switch (doc.getSalesRank()) {
            case 1 -> "[판매 1위]";
            case 2 -> "[판매 2위]";
            case 3 -> "[판매 3위]";
            default -> "";
        };
    }

    /**
     * [보정] 베스트셀러 TOP 6 - Redis 키워드로 ES 실제 데이터 조회 (성능 위기 해결)
     */
    public Mono<SearchPageResponse<BestsellerProductResponse>> getTop6Bestsellers() {
        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(RANKING_KEY, Range.closed(0L, 5L))
                .collectList()
                .flatMap(tuples -> {
                    if (tuples.isEmpty()) return Mono.just(SearchPageResponse.of(List.of(), 0, 0, 6));

                    // 💡 Redis에서 가져온 키워드 리스트 추출
                    List<String> keywords = tuples.stream().map(t -> t.getValue()).toList();

                    // 💡 [핵심] 키워드에 해당하는 실제 상품을 ES에서 termsQuery로 조회
                    NativeQuery query = NativeQuery.builder()
                            .withQuery(q -> q.terms(t -> t.field("title.keyword").terms(v -> v.value(keywords.stream().map(FieldValue::of).toList()))))
                            .build();

                    return operations.search(query, ProductDocument.class)
                            .map(hit -> {
                                ProductDocument doc = hit.getContent();
                                // Redis 순위 정보를 매칭하기 위해 index 활용 가능하나, 여기서는 단순 변환
                                return BestsellerProductResponse.builder()
                                        .id(doc.getId())
                                        .imageUrl(doc.getImageUrl())
                                        .productTitle(resolveProductTitle(doc))
                                        .price(doc.getPrice())
                                        .salesRank(keywords.indexOf(resolveProductTitle(doc)) + 1)
                                        .rankTag("[판매 " + (keywords.indexOf(resolveProductTitle(doc)) + 1) + "위]")
                                        .productUrl("/products/" + doc.getId())
                                        .build();
                            })
                            .collectList()
                            .map(list -> SearchPageResponse.of(list, list.size(), 0, 6));
                });
    }

    /**
     * 홈 화면 베스트셀러 TOP N
     * 임시 랭킹 로직: 최신성 100%
     */
    public Mono<SearchPageResponse<HomeBestsellerProductResponse>> getHomeBestsellers(int size) {
        int targetSize = normalizeSize(size);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(ma -> ma))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, HOME_BESTSELLER_CANDIDATE_SIZE))
                .build();

        return operations.search(query, ProductDocument.class)
                .map(hit -> hit.getContent())
                .collectList()
                .map(candidates -> buildHomeBestsellerResponse(candidates, targetSize));
    }

    /**
     * 우리 아이 취향 저격 제품
     * 고정 브랜드 태그 3개 중 선택 브랜드 상품을 최신순 3개로 반환합니다.
     */
    public Mono<SearchPageResponse<TastePickProductResponse>> getTastePicks(String brandName) {
        String selectedBrand = resolveTastePickBrand(brandName);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(ma -> ma))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, TASTE_PICK_CANDIDATE_SIZE))
                .build();

        return operations.search(query, ProductDocument.class)
                .map(hit -> hit.getContent())
                .filter(doc -> doc.getId() != null)
                .filter(doc -> hasText(doc.getImageUrl()))
                .filter(doc -> selectedBrand.equals(doc.getBrandName()))
                .take(TASTE_PICK_SIZE)
                .map(doc -> TastePickProductResponse.builder()
                        .productId(doc.getId())
                        .imageUrl(doc.getImageUrl())
                        .title(resolveProductTitle(doc))
                        .price(doc.getPrice())
                        .brandName(doc.getBrandName())
                        .productUrl("/products/" + doc.getId())
                        .build())
                .collectList()
                .map(data -> SearchPageResponse.of(
                        data,
                        data.size(),
                        0,
                        TASTE_PICK_SIZE,
                        Map.of(
                                "selectedBrandName", selectedBrand,
                                "tags", buildTastePickTags(selectedBrand),
                                "updatedAt", LocalDateTime.now().format(CREATED_AT_FORMATTER)
                        )
                ));
    }

    public Mono<SearchPageResponse<SimilarProductResponse>> getSimilarProducts(Long productId, int size) {
        int targetSize = normalizeSimilarSize(size);
        if (productId == null) {
            return Mono.just(SearchPageResponse.of(List.of(), 0, 0, targetSize));
        }

        return findProductById(productId)
                .flatMap(base -> findCandidateProducts(base, targetSize)
                        .map(candidates -> buildSimilarProductResponse(base, candidates, targetSize)))
                .switchIfEmpty(Mono.just(SearchPageResponse.of(List.of(), 0, 0, targetSize)));
    }

    public Mono<SearchPageResponse<TogetherProductResponse>> getTogetherProducts(Long productId, int size) {
        int targetSize = normalizeTogetherSize(size);
        if (productId == null) {
            return Mono.just(SearchPageResponse.of(List.of(), 0, 0, targetSize));
        }

        return findProductById(productId)
                .flatMap(base -> findTogetherCandidateProducts(base, targetSize)
                        .flatMap(candidates -> buildTogetherProductResponse(base, candidates, targetSize)))
                .switchIfEmpty(Mono.just(SearchPageResponse.of(List.of(), 0, 0, targetSize)));
    }

    /**
     * 메인 배너 상품 3개
     * 최신순 상품 중 이미지가 있는 항목만 추려 displayOrder를 부여하고 isHero=true로 반환합니다.
     */
    public Mono<SearchPageResponse<MainBannerProductResponse>> getMainHeroBanners() {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(ma -> ma))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, MAIN_BANNER_CANDIDATE_SIZE))
                .build();

        LocalDateTime now = LocalDateTime.now();

        return operations.search(query, ProductBannerDocument.class)
                .map(hit -> hit.getContent())
                .filter(doc -> doc.getProductId() != null)
                .filter(doc -> hasText(doc.getImageUrl()))
                .sort((a, b) -> {
                    long aCreated = a.getCreatedAt() != null ? a.getCreatedAt() : 0L;
                    long bCreated = b.getCreatedAt() != null ? b.getCreatedAt() : 0L;
                    return Long.compare(bCreated, aCreated);
                })
                .take(MAIN_BANNER_HERO_SIZE)
                .collectList()
                .map(products -> {
                    List<MainBannerProductResponse> data = java.util.stream.IntStream.range(0, products.size())
                            .mapToObj(index -> {
                                ProductBannerDocument doc = products.get(index);
                                return MainBannerProductResponse.builder()
                                        .productId(doc.getProductId())
                                        .imageUrl(doc.getImageUrl())
                                        .displayOrder(index + 1)
                                        .isHero(true)
                                        .build();
                            })
                            .toList();

                    return SearchPageResponse.of(
                            data,
                            data.size(),
                            0,
                            MAIN_BANNER_HERO_SIZE,
                            Map.of("updatedAt", now.format(CREATED_AT_FORMATTER))
                    );
                });
    }

    private SearchPageResponse<HomeBestsellerProductResponse> buildHomeBestsellerResponse(
            List<ProductDocument> candidates,
            int targetSize
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return SearchPageResponse.of(
                    List.of(),
                    0,
                    0,
                    targetSize,
                    Map.of("updatedAt", LocalDateTime.now().format(CREATED_AT_FORMATTER))
            );
        }

        LocalDateTime now = LocalDateTime.now();

        List<ScoredProduct> ranked = candidates.stream()
                .map(doc -> scoreProduct(doc, now))
                .sorted((a, b) -> {
                    int byScore = Double.compare(b.score(), a.score());
                    if (byScore != 0) return byScore;
                    return b.createdAt().compareTo(a.createdAt());
                })
                .limit(targetSize)
                .toList();

        List<HomeBestsellerProductResponse> data = java.util.stream.IntStream.range(0, ranked.size())
                .mapToObj(index -> {
                    ScoredProduct p = ranked.get(index);
                    return HomeBestsellerProductResponse.builder()
                            .rank(index + 1)
                            .id(p.doc().getId())
                            .imageUrl(p.doc().getImageUrl())
                            .productTitle(resolveProductTitle(p.doc()))
                            .price(p.doc().getPrice())
                            .score(round4(p.score()))
                            .salesCount(p.salesCount())
                            .createdAt(p.doc().getCreatedAt())
                            .productUrl("/products/" + p.doc().getId())
                            .build();
                })
                .toList();

        return SearchPageResponse.of(
                data,
                data.size(),
                0,
                targetSize,
                Map.of(
                        "updatedAt", now.format(CREATED_AT_FORMATTER),
                        "weights", Map.of("recency", RECENCY_WEIGHT)
                )
        );
    }

    private Mono<ProductDocument> findProductById(Long productId) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("id").value(productId)))
                .withPageable(PageRequest.of(0, 1))
                .build();

        return operations.search(query, ProductDocument.class)
                .next()
                .map(hit -> hit.getContent());
    }

    private Mono<List<ProductDocument>> findCandidateProducts(ProductDocument base, int targetSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    if (hasText(base.getCategory())) {
                        b.filter(f -> f.term(t -> t.field("category").value(base.getCategory())));
                    }
                    b.mustNot(mn -> mn.term(t -> t.field("id").value(base.getId())));
                    return b;
                }))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, Math.max(targetSize * 10, SIMILAR_CANDIDATE_SIZE)))
                .build();

        return operations.search(query, ProductDocument.class)
                .map(hit -> hit.getContent())
                .collectList();
    }

    private SearchPageResponse<SimilarProductResponse> buildSimilarProductResponse(
            ProductDocument base,
            List<ProductDocument> candidates,
            int targetSize
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return SearchPageResponse.of(
                    List.of(),
                    0,
                    0,
                    targetSize,
                    Map.of(
                            "baseProductId", base.getId(),
                            "updatedAt", LocalDateTime.now().format(CREATED_AT_FORMATTER)
                    )
            );
        }

        LocalDateTime now = LocalDateTime.now();
        double baseDiscountRate = calculateDiscountRate(base);
        LocalDateTime baseCreatedAt = parseCreatedAt(base.getCreatedAt());

        List<SimilarScoredProduct> ranked = candidates.stream()
                .map(candidate -> scoreSimilarProduct(base, candidate, baseDiscountRate, baseCreatedAt, now))
                .sorted((a, b) -> {
                    int byScore = Double.compare(b.score(), a.score());
                    if (byScore != 0) return byScore;
                    return b.createdAt().compareTo(a.createdAt());
                })
                .limit(targetSize)
                .toList();

        List<SimilarProductResponse> data = ranked.stream()
                .map(p -> SimilarProductResponse.builder()
                        .productId(p.doc().getId())
                        .imageUrl(p.doc().getImageUrl())
                        .title(p.doc().getTitle())
                        .tags(buildSimilarTags(p.doc()))
                        .price(p.doc().getPrice())
                        .build())
                .toList();

        return SearchPageResponse.of(
                data,
                data.size(),
                0,
                targetSize,
                Map.of(
                        "baseProductId", base.getId(),
                        "weights", Map.of(
                                "category", SIMILAR_CATEGORY_WEIGHT,
                                "price", SIMILAR_PRICE_WEIGHT,
                                "discount", SIMILAR_DISCOUNT_WEIGHT,
                                "recency", SIMILAR_RECENCY_WEIGHT
                        ),
                        "updatedAt", now.format(CREATED_AT_FORMATTER)
                )
        );
    }

    private Mono<List<ProductDocument>> findTogetherCandidateProducts(ProductDocument base, int targetSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.mustNot(mn -> mn.term(t -> t.field("id").value(base.getId())));
                    return b;
                }))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, Math.max(targetSize * 15, TOGETHER_CANDIDATE_SIZE)))
                .build();

        return operations.search(query, ProductDocument.class)
                .map(hit -> hit.getContent())
                .collectList();
    }

    private Mono<SearchPageResponse<TogetherProductResponse>> buildTogetherProductResponse(
            ProductDocument base,
            List<ProductDocument> candidates,
            int targetSize
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(SearchPageResponse.of(
                    List.of(),
                    0,
                    0,
                    targetSize,
                    Map.of(
                            "baseProductId", base.getId(),
                            "updatedAt", LocalDateTime.now().format(CREATED_AT_FORMATTER)
                    )
            ));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseCreatedAt = parseCreatedAt(base.getCreatedAt());

        List<TogetherScoredProduct> ranked = candidates.stream()
                .map(candidate -> scoreTogetherProduct(base, candidate, baseCreatedAt, now))
                .sorted((a, b) -> {
                    int byScore = Double.compare(b.score(), a.score());
                    if (byScore != 0) return byScore;
                    return b.createdAt().compareTo(a.createdAt());
                })
                .limit(targetSize)
                .toList();

        List<Long> rankedProductIds = ranked.stream()
                .map(p -> p.doc().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return findOptionsByProductIds(rankedProductIds)
                .map(optionsByProductId -> ranked.stream()
                        .map(p -> TogetherProductResponse.builder()
                                .productId(p.doc().getId())
                                .imageUrl(p.doc().getImageUrl())
                                .title(resolveProductTitle(p.doc()))
                                .tags(buildSimilarTags(p.doc()))
                                .price(p.doc().getPrice())
                                .options(optionsByProductId.getOrDefault(p.doc().getId(), List.of()))
                                .build())
                        .toList())
                .map(data -> SearchPageResponse.of(
                        data,
                        data.size(),
                        0,
                        targetSize,
                        Map.of(
                                "baseProductId", base.getId(),
                                "weights", Map.of(
                                        "category", TOGETHER_CATEGORY_WEIGHT,
                                        "price", TOGETHER_PRICE_WEIGHT,
                                        "recency", TOGETHER_RECENCY_WEIGHT
                                ),
                                "updatedAt", now.format(CREATED_AT_FORMATTER)
                        )
                ));
    }

    private Mono<Map<Long, List<TogetherProductOptionResponse>>> findOptionsByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<Long> deduplicatedProductIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (deduplicatedProductIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.terms(t -> t
                        .field("product_id")
                        .terms(v -> v.value(deduplicatedProductIds.stream()
                                .map(FieldValue::of)
                                .toList()))
                ))
                .withSort(s -> s.field(f -> f.field("product_id").order(SortOrder.Asc)))
                .withSort(s -> s.field(f -> f.field("option_id").order(SortOrder.Asc)))
                .build();

        return operations.search(query, ProductOptionDocument.class)
                .map(hit -> hit.getContent())
                .collectList()
                .map(optionDocs -> {
                    Map<Long, List<TogetherProductOptionResponse>> grouped = new LinkedHashMap<>();
                    for (Long productId : deduplicatedProductIds) {
                        grouped.put(productId, new ArrayList<>());
                    }

                    for (ProductOptionDocument option : optionDocs) {
                        Long productId = option.getProductId();
                        if (productId == null) {
                            continue;
                        }
                        grouped.computeIfAbsent(productId, key -> new ArrayList<>())
                                .add(TogetherProductOptionResponse.builder()
                                        .optionId(option.getOptionId())
                                        .optionName(option.getOptionName())
                                        .extraPrice(option.getExtraPrice() != null ? option.getExtraPrice() : 0L)
                                        .initialStock(option.getInitialStock())
                                        .build());
                    }
                    return grouped;
                })
                .onErrorResume(ex -> {
                    log.warn("together options batch lookup failed. productIds={}, reason={}", deduplicatedProductIds, ex.getMessage());
                    Map<Long, List<TogetherProductOptionResponse>> emptyMap = new LinkedHashMap<>();
                    for (Long productId : deduplicatedProductIds) {
                        emptyMap.put(productId, List.of());
                    }
                    return Mono.just(emptyMap);
                });
    }

    private List<String> buildSimilarTags(ProductDocument doc) {
        List<String> tags = new java.util.ArrayList<>();

        // 우선순위: NEW > 판매 1/2/3위
        if (isNewProduct(doc)) {
            tags.add("[NEW]");
        }

        String rankTag = determineRankTag(doc);
        if (hasText(rankTag)) {
            tags.add(rankTag);
        }

        return tags;
    }

    private SimilarScoredProduct scoreSimilarProduct(
            ProductDocument base,
            ProductDocument candidate,
            double baseDiscountRate,
            LocalDateTime baseCreatedAt,
            LocalDateTime now
    ) {
        double categoryScore = hasText(base.getCategory()) && base.getCategory().equals(candidate.getCategory()) ? 1.0 : 0.0;
        double priceScore = calculatePriceSimilarity(base.getPrice(), candidate.getPrice());
        double discountScore = calculateDiscountSimilarity(baseDiscountRate, calculateDiscountRate(candidate));

        LocalDateTime candidateCreatedAt = parseCreatedAt(candidate.getCreatedAt());
        double recencyScore = calculateRelativeRecencySimilarity(baseCreatedAt, candidateCreatedAt, now);

        double score =
                (SIMILAR_CATEGORY_WEIGHT * categoryScore) +
                        (SIMILAR_PRICE_WEIGHT * priceScore) +
                        (SIMILAR_DISCOUNT_WEIGHT * discountScore) +
                        (SIMILAR_RECENCY_WEIGHT * recencyScore);

        return new SimilarScoredProduct(candidate, score, candidateCreatedAt);
    }

    private TogetherScoredProduct scoreTogetherProduct(
            ProductDocument base,
            ProductDocument candidate,
            LocalDateTime baseCreatedAt,
            LocalDateTime now
    ) {
        boolean categoryIdMatched =
                base.getCategoryId() != null
                        && candidate.getCategoryId() != null
                        && base.getCategoryId().equals(candidate.getCategoryId());

        double categoryScore = categoryIdMatched ? 1.0 : 0.0;
        if (categoryScore == 0.0 && hasText(base.getCategory()) && base.getCategory().equals(candidate.getCategory())) {
            categoryScore = 0.8;
        }

        double priceScore = calculatePriceSimilarity(base.getPrice(), candidate.getPrice());
        LocalDateTime candidateCreatedAt = parseCreatedAt(candidate.getCreatedAt());
        double recencyScore = calculateRelativeRecencySimilarity(baseCreatedAt, candidateCreatedAt, now);

        double score =
                (TOGETHER_CATEGORY_WEIGHT * categoryScore) +
                        (TOGETHER_PRICE_WEIGHT * priceScore) +
                        (TOGETHER_RECENCY_WEIGHT * recencyScore);

        return new TogetherScoredProduct(candidate, score, candidateCreatedAt);
    }

    private ScoredProduct scoreProduct(ProductDocument doc, LocalDateTime now) {
        LocalDateTime createdAt = parseCreatedAt(doc.getCreatedAt());
        double recencyNorm = calculateRecencyNorm(createdAt, now);

        long salesCount = (doc.getSalesCount() != null && doc.getSalesCount() > 0) ? doc.getSalesCount() : 0L;
        double score = RECENCY_WEIGHT * recencyNorm;
        return new ScoredProduct(doc, score, salesCount, createdAt);
    }

    private LocalDateTime parseCreatedAt(String raw) {
        if (!hasText(raw)) {
            return LocalDateTime.MIN;
        }

        if (raw.chars().allMatch(Character::isDigit)) {
            try {
                long epochMillis = Long.parseLong(raw);
                return LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(epochMillis),
                        ZoneId.systemDefault()
                );
            } catch (Exception ignore) {
                return LocalDateTime.MIN;
            }
        }

        try {
            return LocalDateTime.parse(raw, CREATED_AT_FORMATTER);
        } catch (Exception ignore) {
            try {
                return LocalDateTime.parse(raw);
            } catch (Exception ignoreAgain) {
                return LocalDateTime.MIN;
            }
        }
    }

    private double calculateRecencyNorm(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt.equals(LocalDateTime.MIN)) {
            return 0.0;
        }

        long days = ChronoUnit.DAYS.between(createdAt, now);
        if (days <= 0) {
            return 1.0;
        }
        if (days >= RECENCY_WINDOW_DAYS) {
            return 0.0;
        }
        return 1.0 - ((double) days / RECENCY_WINDOW_DAYS);
    }

    private double calculateRelativeRecencySimilarity(LocalDateTime baseCreatedAt, LocalDateTime candidateCreatedAt, LocalDateTime now) {
        if (baseCreatedAt.equals(LocalDateTime.MIN) || candidateCreatedAt.equals(LocalDateTime.MIN)) {
            return 0.0;
        }

        long baseAge = Math.max(0, ChronoUnit.DAYS.between(baseCreatedAt, now));
        long candidateAge = Math.max(0, ChronoUnit.DAYS.between(candidateCreatedAt, now));
        long diff = Math.abs(baseAge - candidateAge);

        if (diff >= RECENCY_WINDOW_DAYS) {
            return 0.0;
        }
        return 1.0 - ((double) diff / RECENCY_WINDOW_DAYS);
    }

    private double calculatePriceSimilarity(Long basePrice, Long candidatePrice) {
        if (basePrice == null || candidatePrice == null || basePrice <= 0 || candidatePrice <= 0) {
            return 0.0;
        }

        double ratio = Math.abs((double) candidatePrice - basePrice) / basePrice;
        return Math.max(0.0, 1.0 - Math.min(ratio, 1.0));
    }

    private double calculateDiscountSimilarity(double baseDiscountRate, double candidateDiscountRate) {
        return Math.max(0.0, 1.0 - Math.min(Math.abs(candidateDiscountRate - baseDiscountRate), 1.0));
    }

    private double calculateDiscountRate(ProductDocument doc) {
        if (doc == null || doc.getOriginalPrice() == null || doc.getPrice() == null || doc.getOriginalPrice() <= 0) {
            return 0.0;
        }

        double rate = (double) (doc.getOriginalPrice() - doc.getPrice()) / doc.getOriginalPrice();
        if (rate < 0.0) {
            return 0.0;
        }
        return Math.min(rate, 1.0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) return HOME_BESTSELLER_DEFAULT_SIZE;
        return Math.min(size, HOME_BESTSELLER_MAX_SIZE);
    }

    private int normalizeSimilarSize(int size) {
        if (size <= 0) return SIMILAR_DEFAULT_SIZE;
        return Math.min(size, SIMILAR_MAX_SIZE);
    }

    private int normalizeTogetherSize(int size) {
        if (size <= 0) return TOGETHER_DEFAULT_SIZE;
        return Math.min(size, TOGETHER_MAX_SIZE);
    }

    private String resolveTastePickBrand(String requestedBrandName) {
        if (!hasText(requestedBrandName)) {
            return TASTE_PICK_BRANDS.get(0);
        }

        String trimmed = requestedBrandName.trim();
        final String normalizedBrand = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        return TASTE_PICK_BRANDS.stream()
                .filter(allowed -> allowed.equals(normalizedBrand))
                .findFirst()
                .orElse(TASTE_PICK_BRANDS.get(0));
    }

    private List<TastePickTagResponse> buildTastePickTags(String selectedBrand) {
        return TASTE_PICK_BRANDS.stream()
                .map(brand -> TastePickTagResponse.builder()
                        .brandName(brand)
                        .tagName("#" + brand)
                        .selected(brand.equals(selectedBrand))
                        .build())
                .toList();
    }

    private double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private boolean isNewProduct(ProductDocument doc) {
        LocalDateTime createdAt = parseCreatedAt(doc.getCreatedAt());
        if (createdAt.equals(LocalDateTime.MIN)) {
            return false;
        }
        return createdAt.isAfter(LocalDateTime.now().minusDays(7));
    }

    private record ScoredProduct(
            ProductDocument doc,
            double score,
            long salesCount,
            LocalDateTime createdAt
    ) {
    }

    private record SimilarScoredProduct(
            ProductDocument doc,
            double score,
            LocalDateTime createdAt
    ) {
    }

    private record TogetherScoredProduct(
            ProductDocument doc,
            double score,
            LocalDateTime createdAt
    ) {
    }

    /**
     * [보정] 데이터 매핑 로직 (할인율 및 신상품 판별)
     */
    private ProductSearchResponse mapToProductResponse(ProductDocument doc, String tag) {
        // 💡 할인율 계산 (정가 대비 할인가)
        double discountRate = 0.0;
        if (doc.getOriginalPrice() != null && doc.getOriginalPrice() > 0) {
            discountRate = (double) (doc.getOriginalPrice() - doc.getPrice()) / doc.getOriginalPrice();
        }

        // 💡 신상품 판별 (예: 생성일 기준 7일 이내)
        boolean isNew = isNewProduct(doc);

        return ProductSearchResponse.builder()
                .id(doc.getId())
                .imageUrl(doc.getImageUrl())
                .productTitle(resolveProductTitle(doc))
                .originalPrice(doc.getOriginalPrice())
                .price(doc.getPrice())
                .discountRate(Math.round(discountRate * 100) / 100.0) // 소수점 2자리
                .discountTag(discountRate > 0 ? "최적할인가" : null)
                .isNew(isNew)
                .productTag(tag)
                .productUrl("/products/" + doc.getId())
                .category(resolveTopCategoryName(doc))
                .build();
    }

    private String resolveProductTitle(ProductDocument doc) {
        if (doc == null) return null;
        if (hasText(doc.getTitle())) return doc.getTitle();
        return doc.getProductName();
    }

    private String resolveSearchKeyword(ProductSearchCondition condition) {
        if (condition == null) return null;
        if (hasText(condition.title())) return condition.title().trim();
        if (hasText(condition.keyword())) return condition.keyword().trim();
        return null;
    }

    private String resolveTopCategoryName(ProductDocument doc) {
        if (doc == null) return null;
        Long categoryId = doc.getCategoryId();
        if (categoryId != null) {
            if (SNACK_JERKY_CATEGORY_IDS.contains(categoryId)) return "Snack & Jerky";
            if (MEAL_CATEGORY_IDS.contains(categoryId)) return "Meal";
            if (BAKERY_CATEGORY_IDS.contains(categoryId)) return "Bakery";
        }

        String keywords = doc.getKeywords();
        String title = resolveProductTitle(doc);

        if (containsIgnoreSpace(keywords, "베이커리") || containsIgnoreSpace(title, "베이커리")) return "Bakery";
        if (containsIgnoreSpace(keywords, "강아지식사")
                || containsIgnoreSpace(title, "테린")
                || containsIgnoreSpace(title, "스팀")
                || containsIgnoreSpace(title, "그린빈")) return "Meal";
        if (containsIgnoreSpace(keywords, "강아지간식")) return "Snack & Jerky";
        return null;
    }

    private String normalizeCategoryKey(String raw) {
        if (!hasText(raw)) return "";
        return raw.toLowerCase()
                .replace("&", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    private boolean matchesSearchKeyword(ProductDocument doc, String keyword) {
        if (!hasText(keyword)) return true;
        return containsIgnoreSpace(resolveProductTitle(doc), keyword)
                || containsIgnoreSpace(doc.getKeywords(), keyword)
                || containsIgnoreSpace(doc.getContent(), keyword);
    }

    private boolean matchesCategory(ProductDocument doc, String category) {
        if (!hasText(category) || "ALL".equalsIgnoreCase(category.trim())) return true;
        String docCategory = resolveTopCategoryName(doc);
        return normalizeCategoryKey(docCategory).equals(normalizeCategoryKey(category));
    }

    private boolean matchesSubCategory(ProductDocument doc, String subCategory) {
        if (!hasText(subCategory)) return true;
        if (doc.getCategoryId() == null) return false;

        String normalized = subCategory.trim().toLowerCase();
        Long mappedId = SUB_CATEGORY_CODE_TO_ID.get(normalized);
        if (mappedId != null) {
            return mappedId.equals(doc.getCategoryId());
        }

        // 하위 호환: 숫자 id가 들어온 경우도 허용
        if (normalized.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(normalized) == doc.getCategoryId();
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean containsIgnoreSpace(String source, String token) {
        if (!hasText(source) || !hasText(token)) return false;
        String normalizedSource = source.replace(" ", "").toLowerCase();
        String normalizedToken = token.replace(" ", "").toLowerCase();
        return normalizedSource.contains(normalizedToken);
    }
}
