package com.eum.searchserver.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.eum.searchserver.domain.ReviewSearchDocument;
import com.eum.searchserver.dto.request.ReviewSearchCondition;
import com.eum.searchserver.dto.response.ReviewHeaderResponse;
import com.eum.searchserver.dto.response.ReviewSearchResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ReviewSearchService {

    private static final int DEFAULT_SIZE = 3;
    private static final int MAX_MEDIA_COUNT = 5;
    private static final String MEDIA_URL_DELIMITER = "\\|";

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
                .createdAt(doc.getCreatedAt())
                .reviewDetailUrl("/reviews/" + doc.getId())
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
            b.filter(f -> f.term(t -> t.field("media_type").value(mediaType)));
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
        // Soft-deleted reviews are excluded by default.
        b.mustNot(mn -> mn.exists(e -> e.field("deleted_at")));
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
}
