package com.eum.searchserver.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.eum.searchserver.domain.FaqDocument;
import com.eum.searchserver.dto.request.FaqSearchCondition;
import com.eum.searchserver.dto.response.FaqDetailResponse;
import com.eum.searchserver.dto.response.FaqResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final ReactiveElasticsearchOperations operations;
    private static final String FAQ_DETAIL_URL_PREFIX = "/api/v1/faq/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yy/MM/dd");

    public Mono<SearchPageResponse<FaqResponse>> searchFaqs(FaqSearchCondition condition) {
        int safePage = condition.page() == null ? 0 : Math.max(condition.page(), 0);
        int safeSize = condition.size() == null || condition.size() <= 0 ? 12 : condition.size();
        Pageable pageable = PageRequest.of(safePage, safeSize);

        NativeQuery fixedQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("is_pinned").value(true)));
                    applySearchFilters(b, condition);
                    return b;
                }))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .build();

        NativeQuery generalQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.mustNot(m -> m.term(t -> t.field("is_pinned").value(true)));
                    applySearchFilters(b, condition);
                    return b;
                }))
                .withPageable(pageable)
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .build();

        return Mono.zip(
                operations.search(fixedQuery, FaqDocument.class)
                        .map(hit -> mapToFaqResponse(hit.getContent()))
                        .collectList(),
                operations.searchForPage(generalQuery, FaqDocument.class)
        ).map(tuple -> {
            List<FaqResponse> fixedFaqs = tuple.getT1();
            var generalPage = tuple.getT2();
            long totalElements = fixedFaqs.size() + generalPage.getTotalElements();

            List<FaqResponse> combined = new ArrayList<>();
            combined.addAll(fixedFaqs);
            combined.addAll(generalPage.getSearchHits().stream()
                    .map(hit -> mapToFaqResponse(hit.getContent()))
                    .toList());

            return SearchPageResponse.of(combined, totalElements, safePage, safeSize);
        });
    }

    public Mono<FaqDetailResponse> getFaqDetail(Long faqId) {
        NativeQuery detailQuery = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("faq_id").value(faqId)))
                .withPageable(PageRequest.of(0, 1))
                .build();

        return operations.search(detailQuery, FaqDocument.class)
                .next()
                .map(hit -> mapToFaqDetail(hit.getContent()));
    }

    private FaqDetailResponse mapToFaqDetail(FaqDocument doc) {
        return FaqDetailResponse.builder()
                .faqId(doc.getId())
                .title(doc.getTitle())
                .author(doc.getAuthor())
                .createdAt(formatShortDate(doc.getCreatedAt()))
                .viewCount(doc.getViewCount() == null ? 0L : doc.getViewCount())
                .content(doc.getContent())
                .contentImageUrls(doc.getContentImageUrls() == null ? List.of() : doc.getContentImageUrls())
                .build();
    }

    private FaqResponse mapToFaqResponse(FaqDocument doc) {
        return FaqResponse.builder()
                .faqId(doc.getId())
                .title(doc.getTitle())
                .author(doc.getAuthor() == null ? "관리자" : doc.getAuthor())
                .createdAt(formatShortDate(doc.getCreatedAt()))
                .viewCount(doc.getViewCount() == null ? 0L : doc.getViewCount())
                .faqDetailUrl(FAQ_DETAIL_URL_PREFIX + doc.getId())
                .build();
    }

    private void applySearchFilters(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b, FaqSearchCondition condition) {
        if (hasText(condition.keyword())) {
            String keyword = condition.keyword().trim();
            b.must(m -> m.bool(search -> {
                if ("제목".equals(condition.searchType())) {
                    search.should(s -> s.term(t -> t.field("title.keyword").value(keyword).boost(100f)));
                    search.should(s -> s.match(mm -> mm.field("title").query(keyword).analyzer("nori_analyzer").boost(40f)));
                    search.should(s -> s.match(mm -> mm.field("title.edge").query(keyword).boost(20f)));
                    search.should(s -> s.match(mm -> mm.field("title.partial").query(keyword).boost(10f)));
                } else {
                    search.should(s -> s.term(t -> t.field("title.keyword").value(keyword).boost(100f)));
                    search.should(s -> s.match(mm -> mm.field("title").query(keyword).analyzer("nori_analyzer").boost(40f)));
                    search.should(s -> s.match(mm -> mm.field("content").query(keyword).analyzer("nori_analyzer").boost(20f)));
                    search.should(s -> s.match(mm -> mm.field("title.edge").query(keyword).boost(20f)));
                    search.should(s -> s.match(mm -> mm.field("title.partial").query(keyword).boost(10f)));
                    search.should(s -> s.match(mm -> mm.field("content.edge").query(keyword).boost(8f)));
                    search.should(s -> s.match(mm -> mm.field("content.partial").query(keyword).boost(5f)));
                }
                search.minimumShouldMatch("1");
                return search;
            }));
        }

        if (hasText(condition.searchRange()) && !"전체".equals(condition.searchRange())) {
            String fromDate = calculateFromDate(condition.searchRange());
            if (fromDate != null) {
                b.filter(f -> f.range(r -> r
                        .date(d -> d.field("created_at").gte(fromDate))
                ));
            }
        }
    }

    private String calculateFromDate(String range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetDate = switch (range) {
            case "일주일" -> now.minusWeeks(1);
            case "한달" -> now.minusMonths(1);
            case "세달" -> now.minusMonths(3);
            default -> null;
        };

        if (targetDate == null) return null;
        long millis = targetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return String.valueOf(millis);
    }

    private String formatShortDate(String createdAt) {
        if (!hasText(createdAt)) {
            return "";
        }

        try {
            long millis = Long.parseLong(createdAt);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                    .format(DATE_FORMATTER);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(createdAt).format(DATE_FORMATTER);
            } catch (Exception ignoredAgain) {
                return createdAt;
            }
        }
    }
}
