package com.eum.searchserver.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.eum.searchserver.domain.OrderDetailSearchDocument;
import com.eum.searchserver.domain.OrderSearchDocument;
import com.eum.searchserver.domain.ProductDocument;
import com.eum.searchserver.domain.ReviewSearchDocument;
import com.eum.searchserver.dto.request.ReviewSearchCondition;
import com.eum.searchserver.dto.response.ReviewHeaderResponse;
import com.eum.searchserver.dto.response.ReviewHighlightItemResponse;
import com.eum.searchserver.dto.response.ReviewHighlightsResponse;
import com.eum.searchserver.dto.response.ReviewSearchResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSearchService {

    private static final int DEFAULT_SIZE = 3;
    private static final int MAX_MEDIA_COUNT = 5;
    private static final int REVIEW_HIGHLIGHT_SIZE = 5;
    private static final int ORDER_CANDIDATE_SIZE = 10_000;
    private static final int ORDER_TERMS_CHUNK_SIZE = 300;
    private static final String MEDIA_URL_DELIMITER = "\\|";
    private static final double PHOTO_WEIGHT_SALES = 0.15;
    private static final double PHOTO_WEIGHT_REVENUE = 0.10;
    private static final double PHOTO_WEIGHT_RECENCY = 0.10;
    private static final double PHOTO_WEIGHT_BUYERS = 0.10;
    private static final double PHOTO_WEIGHT_STAR_AVG = 0.30;
    private static final double PHOTO_WEIGHT_REVIEW_COUNT = 0.25;

    private final ReactiveElasticsearchOperations operations;

    public Mono<SearchPageResponse<ReviewSearchResponse>> searchReviews(ReviewSearchCondition condition) {
        int safePage = (condition.page() == null) ? 0 : Math.max(condition.page(), 0);
        int safeSize = (condition.size() == null || condition.size() <= 0) ? DEFAULT_SIZE : condition.size();
        Pageable pageable = PageRequest.of(safePage, safeSize);
        boolean bestSort = "BEST".equalsIgnoreCase(condition.sortType());

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> buildListFilter(b, condition, bestSort)))
                .withSort(s -> {
                    if (bestSort) {
                        return s.field(f -> f.field("like_count").order(SortOrder.Desc));
                    }
                    return s.field(f -> f.field("created_at").order(SortOrder.Desc));
                })
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(pageable)
                .build();

        return operations.searchForPage(query, ReviewSearchDocument.class)
                .map(searchPage -> {
                    List<ReviewSearchResponse> data = searchPage.getSearchHits().stream()
                            .map(hit -> mapToResponse(hit.getContent()))
                            .toList();
                    return SearchPageResponse.of(data, searchPage.getTotalElements(), safePage, safeSize);
                });
    }

    public Mono<ReviewHeaderResponse> getReviewHeader(ReviewSearchCondition condition) {
        Mono<Long> count5 = countByStar(condition, 5);
        Mono<Long> count4 = countByStar(condition, 4);
        Mono<Long> count3 = countByStar(condition, 3);
        Mono<Long> count2 = countByStar(condition, 2);
        Mono<Long> count1 = countByStar(condition, 1);

        return Mono.zip(count5, count4, count3, count2, count1)
                .map(tuple -> {
                    long star5 = tuple.getT1();
                    long star4 = tuple.getT2();
                    long star3 = tuple.getT3();
                    long star2 = tuple.getT4();
                    long star1 = tuple.getT5();
                    long total = star5 + star4 + star3 + star2 + star1;

                    double avg = (total == 0) ? 0.0 :
                            ((5.0 * star5) + (4.0 * star4) + (3.0 * star3) + (2.0 * star2) + (1.0 * star1)) / total;

                    Map<Integer, Double> distribution = new LinkedHashMap<>();
                    distribution.put(5, ratio(star5, total));
                    distribution.put(4, ratio(star4, total));
                    distribution.put(3, ratio(star3, total));
                    distribution.put(2, ratio(star2, total));
                    distribution.put(1, ratio(star1, total));

                    return ReviewHeaderResponse.builder()
                            .avgRating(round(avg))
                            .totalCount(total)
                            .ratingDistribution(distribution)
                            .build();
                });
    }

    public Mono<ReviewHighlightsResponse> getBestPhotoHighlights() {
        return fetchImageReviews()
                .flatMap(reviews -> {
                    if (reviews.isEmpty()) {
                        return Mono.just(emptyHighlights());
                    }
                    return fetchCompletedOrders()
                            .flatMap(orders -> {
                                Map<Long, OrderSearchDocument> orderByOrderId = orders.stream()
                                        .filter(o -> o.getOrderId() != null)
                                        .collect(Collectors.toMap(OrderSearchDocument::getOrderId, o -> o, (a, b) -> a));

                                return fetchOrderDetailsByOrderIds(new ArrayList<>(orderByOrderId.keySet()))
                                        .flatMap(details -> buildReviewHighlights(reviews, orderByOrderId, details));
                            });
                });
    }

    private ReviewSearchResponse mapToResponse(ReviewSearchDocument doc) {
        List<String> mediaUrls = resolveReviewMediaUrls(doc);
        return ReviewSearchResponse.builder()
                .reviewId(doc.getId())
                .productId(doc.getProductId())
                .writerName(doc.getWriterName())
                .star(doc.getStar())
                .likeCount(doc.getLikeCount())
                .reviewMediaUrls(mediaUrls)
                .mediaType(doc.getMediaType())
                .content(doc.getContent())
                .createdAt(doc.getCreatedAt() == null ? null : String.valueOf(doc.getCreatedAt()))
                .reviewDetailUrl(resolveReviewDetailUrl(doc))
                .build();
    }

    private List<String> resolveReviewMediaUrls(ReviewSearchDocument doc) {
        if (StringUtils.hasText(doc.getReviewMediaUrls())) {
            return Arrays.stream(doc.getReviewMediaUrls().split(MEDIA_URL_DELIMITER))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .limit(MAX_MEDIA_COUNT)
                    .toList();
        }
        if (StringUtils.hasText(doc.getReviewMediaUrl())) {
            return List.of(doc.getReviewMediaUrl());
        }
        return List.of();
    }

    private String normalizeReviewType(String reviewType) {
        return switch (reviewType.toUpperCase()) {
            case "PHOTO", "IMAGE" -> "IMAGE";
            case "VIDEO" -> "VIDEO";
            case "TEXT" -> "TEXT";
            default -> null;
        };
    }

    private String resolveMediaTypeFilter(String reviewType, boolean bestSort) {
        // "베스트 포토리뷰" 정책: BEST 정렬 요청은 항상 이미지 리뷰만 노출
        if (bestSort) {
            return "IMAGE";
        }
        if (!StringUtils.hasText(reviewType) || "ALL".equalsIgnoreCase(reviewType)) {
            return null;
        }
        return normalizeReviewType(reviewType);
    }

    private Mono<Long> countByStar(ReviewSearchCondition condition, int star) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> buildHeaderFilter(b, condition, star)))
                .build();
        return operations.count(query, ReviewSearchDocument.class);
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder buildListFilter(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            ReviewSearchCondition condition,
            boolean bestSort
    ) {
        applyBaseReviewFilter(b, condition);

        if (StringUtils.hasText(condition.keyword())) {
            String keyword = condition.keyword().trim();
            b.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("content^3", "writer_name")
            ));
        } else {
            b.must(m -> m.matchAll(ma -> ma));
        }

        String mediaType = resolveMediaTypeFilter(condition.reviewType(), bestSort);
        if (mediaType != null) {
            b.filter(f -> f.term(t -> t.field("media_type.keyword").value(mediaType)));
        }
        return b;
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder buildHeaderFilter(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            ReviewSearchCondition condition,
            int star
    ) {
        applyBaseReviewFilter(b, condition);
        b.filter(f -> f.term(t -> t.field("star").value(star)));
        return b;
    }

    private void applyBaseReviewFilter(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            ReviewSearchCondition condition
    ) {
        if (condition.productId() != null) {
            b.filter(f -> f.term(t -> t.field("product_id").value(condition.productId())));
        }
        // NOTE: CDC 문서에서 deleted_at=null 필드가 항상 포함되어 exists 필터가 오작동할 수 있다.
    }

    private Mono<List<ReviewSearchDocument>> fetchImageReviews() {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("media_type.keyword").value("IMAGE")))
                ))
                .withPageable(PageRequest.of(0, ORDER_CANDIDATE_SIZE))
                .build();

        return operations.search(query, ReviewSearchDocument.class)
                .map(hit -> hit.getContent())
                .collectList()
                .onErrorResume(ex -> {
                    log.warn("fetchImageReviews failed", ex);
                    return Mono.just(List.of());
                });
    }

    private Mono<List<OrderSearchDocument>> fetchCompletedOrders() {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.match(mm -> mm.field("order_state").query("ORDER_COMPLETED")))
                        .mustNot(mn -> mn.term(t -> t.field("delete_yn").value("Y")))
                ))
                .withPageable(PageRequest.of(0, ORDER_CANDIDATE_SIZE))
                .build();

        return operations.search(query, OrderSearchDocument.class)
                .map(hit -> hit.getContent())
                .collectList()
                .onErrorReturn(List.of());
    }

    private Mono<List<OrderDetailSearchDocument>> fetchOrderDetailsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Mono.just(List.of());
        }

        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < orderIds.size(); i += ORDER_TERMS_CHUNK_SIZE) {
            chunks.add(orderIds.subList(i, Math.min(i + ORDER_TERMS_CHUNK_SIZE, orderIds.size())));
        }

        return reactor.core.publisher.Flux.fromIterable(chunks)
                .concatMap(chunk -> {
                    NativeQuery query = NativeQuery.builder()
                            .withQuery(q -> q.terms(t -> t
                                    .field("order_id")
                                    .terms(v -> v.value(chunk.stream().map(FieldValue::of).toList()))
                            ))
                            .withPageable(PageRequest.of(0, ORDER_CANDIDATE_SIZE))
                            .build();
                    return operations.search(query, OrderDetailSearchDocument.class).map(hit -> hit.getContent());
                })
                .collectList()
                .map(ArrayList::new);
    }

    private Mono<ReviewHighlightsResponse> buildReviewHighlights(
            List<ReviewSearchDocument> reviews,
            Map<Long, OrderSearchDocument> orderByOrderId,
            List<OrderDetailSearchDocument> details
    ) {
        Map<Long, ReviewAggregate> reviewAggregates = new HashMap<>();
        for (ReviewSearchDocument review : reviews) {
            if (review == null || review.getProductId() == null) {
                continue;
            }
            ReviewAggregate agg = reviewAggregates.computeIfAbsent(review.getProductId(), ReviewAggregate::new);
            agg.reviewCount++;
            agg.starSum += safeLong(review.getStar());
            if (agg.representative == null || compareReviewPriority(review, agg.representative) > 0) {
                agg.representative = review;
            }
        }

        if (reviewAggregates.isEmpty()) {
            return Mono.just(emptyHighlights());
        }

        Map<Long, OrderAggregate> orderAggregates = new HashMap<>();
        for (OrderDetailSearchDocument detail : details) {
            if (detail == null || detail.getProductId() == null || detail.getOrderId() == null) {
                continue;
            }
            OrderSearchDocument order = orderByOrderId.get(detail.getOrderId());
            if (order == null) {
                continue;
            }
            OrderAggregate agg = orderAggregates.computeIfAbsent(detail.getProductId(), OrderAggregate::new);
            agg.sales += safeLong(detail.getQuantity());
            agg.revenue += safeLong(detail.getTotalPrice());
            if (order.getUserId() != null) {
                agg.buyers.add(order.getUserId());
            }
            LocalDateTime orderedAt = parseOrderTime(order.getTime());
            if (orderedAt.isAfter(agg.latestOrderAt)) {
                agg.latestOrderAt = orderedAt;
            }
        }

        List<CombinedCandidate> candidates = reviewAggregates.values().stream()
                .map(r -> toCombined(r, orderAggregates.get(r.productId)))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        double minSales = candidates.stream().mapToDouble(c -> c.sales).min().orElse(0.0);
        double maxSales = candidates.stream().mapToDouble(c -> c.sales).max().orElse(0.0);
        double minRevenue = candidates.stream().mapToDouble(c -> c.revenue).min().orElse(0.0);
        double maxRevenue = candidates.stream().mapToDouble(c -> c.revenue).max().orElse(0.0);
        double minBuyers = candidates.stream().mapToDouble(c -> c.buyers).min().orElse(0.0);
        double maxBuyers = candidates.stream().mapToDouble(c -> c.buyers).max().orElse(0.0);
        double minRecency = candidates.stream().mapToDouble(c -> calculateRecencyNorm(c.latestOrderAt, now)).min().orElse(0.0);
        double maxRecency = candidates.stream().mapToDouble(c -> calculateRecencyNorm(c.latestOrderAt, now)).max().orElse(0.0);
        double minStar = candidates.stream().mapToDouble(c -> c.starAvg).min().orElse(0.0);
        double maxStar = candidates.stream().mapToDouble(c -> c.starAvg).max().orElse(0.0);
        double minReviewCount = candidates.stream().mapToDouble(c -> c.reviewCount).min().orElse(0.0);
        double maxReviewCount = candidates.stream().mapToDouble(c -> c.reviewCount).max().orElse(0.0);

        candidates.forEach(c -> {
            double salesNorm = normalize(c.sales, minSales, maxSales);
            double revenueNorm = normalize(c.revenue, minRevenue, maxRevenue);
            double buyersNorm = normalize(c.buyers, minBuyers, maxBuyers);
            double recencyNorm = normalize(calculateRecencyNorm(c.latestOrderAt, now), minRecency, maxRecency);
            double starNorm = normalize(c.starAvg, minStar, maxStar);
            double reviewCountNorm = normalize(c.reviewCount, minReviewCount, maxReviewCount);

            c.score = (PHOTO_WEIGHT_SALES * salesNorm)
                    + (PHOTO_WEIGHT_REVENUE * revenueNorm)
                    + (PHOTO_WEIGHT_RECENCY * recencyNorm)
                    + (PHOTO_WEIGHT_BUYERS * buyersNorm)
                    + (PHOTO_WEIGHT_STAR_AVG * starNorm)
                    + (PHOTO_WEIGHT_REVIEW_COUNT * reviewCountNorm);
        });

        List<CombinedCandidate> top = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((CombinedCandidate c) -> c.score).reversed()
                        .thenComparingDouble(c -> c.starAvg).reversed()
                        .thenComparingLong(c -> c.reviewCount).reversed())
                .limit(REVIEW_HIGHLIGHT_SIZE)
                .toList();

        List<Long> productIds = top.stream().map(c -> c.productId).filter(Objects::nonNull).distinct().toList();
        return fetchProductsByIds(productIds)
                .map(productsById -> {
                    List<ReviewHighlightItemResponse> items = top.stream()
                            .map(c -> {
                                ProductDocument product = productsById.get(c.productId);
                                String title = resolveProductTitle(product);
                                String image = resolveImageUrl(c.representative, product);
                                String rating = "★ " + round1(c.starAvg) + "(" + c.reviewCount + ")";
                                Long reviewId = c.representative != null ? c.representative.getId() : null;
                                String href = resolveReviewDetailUrl(c.representative);
                                return ReviewHighlightItemResponse.builder()
                                        .id(reviewId)
                                        .img(image)
                                        .title(title)
                                        .rating(rating)
                                        .href(href)
                                        .build();
                            })
                            .toList();

                    return ReviewHighlightsResponse.builder()
                            .status("success")
                            .title("베스트 포토리뷰")
                            .items(items)
                            .build();
                });
    }

    private Mono<Map<Long, ProductDocument>> fetchProductsByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.terms(t -> t
                        .field("id")
                        .terms(v -> v.value(productIds.stream().map(FieldValue::of).toList()))
                ))
                .withPageable(PageRequest.of(0, productIds.size()))
                .build();
        return operations.search(query, ProductDocument.class)
                .map(hit -> hit.getContent())
                .collectMap(ProductDocument::getId, p -> p);
    }

    private ReviewHighlightsResponse emptyHighlights() {
        return ReviewHighlightsResponse.builder()
                .status("success")
                .title("베스트 포토리뷰")
                .items(List.of())
                .build();
    }

    private CombinedCandidate toCombined(ReviewAggregate review, OrderAggregate order) {
        CombinedCandidate c = new CombinedCandidate();
        c.productId = review.productId;
        c.reviewCount = review.reviewCount;
        c.starAvg = review.reviewCount == 0 ? 0.0 : (double) review.starSum / review.reviewCount;
        c.representative = review.representative;
        if (order != null) {
            c.sales = order.sales;
            c.revenue = order.revenue;
            c.buyers = order.buyers.size();
            c.latestOrderAt = order.latestOrderAt;
        } else {
            c.latestOrderAt = LocalDateTime.MIN;
        }
        return c;
    }

    private int compareReviewPriority(ReviewSearchDocument a, ReviewSearchDocument b) {
        long likeA = safeLong(a.getLikeCount());
        long likeB = safeLong(b.getLikeCount());
        if (likeA != likeB) {
            return Long.compare(likeA, likeB);
        }
        return Long.compare(safeLong(a.getCreatedAt()), safeLong(b.getCreatedAt()));
    }

    private String resolveImageUrl(ReviewSearchDocument review, ProductDocument product) {
        if (review != null) {
            List<String> urls = resolveReviewMediaUrls(review);
            if (!urls.isEmpty()) {
                return urls.get(0);
            }
        }
        return product != null ? product.getImageUrl() : null;
    }

    private String resolveReviewDetailUrl(ReviewSearchDocument review) {
        if (review == null || !StringUtils.hasText(review.getPublicId())) {
            return "/review";
        }
        return "/reviews/" + review.getPublicId();
    }

    private String resolveProductTitle(ProductDocument product) {
        if (product == null) {
            return "";
        }
        if (StringUtils.hasText(product.getProductName())) {
            return product.getProductName();
        }
        return StringUtils.hasText(product.getTitle()) ? product.getTitle() : "";
    }

    private LocalDateTime parseOrderTime(Long epochMicros) {
        if (epochMicros == null || epochMicros <= 0) {
            return LocalDateTime.MIN;
        }
        long epochMillis = epochMicros / 1_000L;
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private double calculateRecencyNorm(LocalDateTime target, LocalDateTime now) {
        if (target == null || target.equals(LocalDateTime.MIN)) {
            return 0.0;
        }
        long days = Math.max(0L, ChronoUnit.DAYS.between(target, now));
        return 1.0 / (1.0 + days);
    }

    private double normalize(double value, double min, double max) {
        if (max <= min) {
            return value > 0 ? 1.0 : 0.0;
        }
        return (value - min) / (max - min);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private long safeLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double ratio(long value, long total) {
        if (total == 0) {
            return 0.0;
        }
        return round((value * 100.0) / total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class ReviewAggregate {
        private final Long productId;
        private long reviewCount;
        private long starSum;
        private ReviewSearchDocument representative;

        private ReviewAggregate(Long productId) {
            this.productId = productId;
        }
    }

    private static final class OrderAggregate {
        private final Long productId;
        private long sales;
        private long revenue;
        private final Set<Long> buyers = new HashSet<>();
        private LocalDateTime latestOrderAt = LocalDateTime.MIN;

        private OrderAggregate(Long productId) {
            this.productId = productId;
        }
    }

    private static final class CombinedCandidate {
        private Long productId;
        private long sales;
        private long revenue;
        private long buyers;
        private LocalDateTime latestOrderAt;
        private long reviewCount;
        private double starAvg;
        private double score;
        private ReviewSearchDocument representative;
    }
}
