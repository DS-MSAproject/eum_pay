package com.eum.searchserver.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.eum.searchserver.domain.NoticeDocument;
import com.eum.searchserver.dto.request.NoticeSearchCondition;
import com.eum.searchserver.dto.response.NoticeResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class NoticeService {

    private final ReactiveElasticsearchOperations operations;
    private static final String NOTICE_DETAIL_URL_PREFIX = "/api/v1/notices/";

    public Mono<SearchPageResponse<NoticeResponse>> searchNotices(NoticeSearchCondition condition) {
        // 💡 [보정 1] NPE 방지: page와 size 변수를 상단에서 확실히 고정
        int safePage = (condition.page() == null) ? 0 : Math.max(condition.page(), 0);
        int safeSize = (condition.size() == null || condition.size() <= 0) ? 12 : condition.size();

        Pageable pageable = PageRequest.of(safePage, safeSize);

        // 1. [고정 게시물]
        NativeQuery fixedQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("is_pinned").value(true)));
                    applySearchFilters(b, condition);
                    return b;
                }))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .build();

        // 2. [일반 게시물]
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
                operations.search(fixedQuery, NoticeDocument.class)
                        .map(hit -> mapToResponse(hit.getContent())).collectList(),
                operations.searchForPage(generalQuery, NoticeDocument.class)
        ).map(tuple -> {
            List<NoticeResponse> fixedNotices = tuple.getT1();
            var generalPage = tuple.getT2();
            long totalElements = fixedNotices.size() + generalPage.getTotalElements();

            List<NoticeResponse> combinedContent = new ArrayList<>();
            combinedContent.addAll(fixedNotices);
            combinedContent.addAll(generalPage.getSearchHits().stream()
                    .map(hit -> mapToResponse(hit.getContent()))
                    .toList());

            // 💡 [보정 2] 응답 객체 생성 시 null이 아닌 safePage, safeSize를 전달
            return SearchPageResponse.of(
                    combinedContent,
                    totalElements,
                    safePage,
                    safeSize
            );
        });
    }

    private void applySearchFilters(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b, NoticeSearchCondition condition) {
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
                        .date(d -> d
                                .field("created_at")
                                .gte(fromDate) // 💡 date 빌더에서는 문자열을 바로 gte에 넣을 수 있습니다.
                        )
                ));
            }
        }
    }

    private NoticeResponse mapToResponse(NoticeDocument doc) {
        String formattedDate = "";
        if (hasText(doc.getCreatedAt())) {
            try {
                long timestamp = Long.parseLong(doc.getCreatedAt());
                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                formattedDate = doc.getCreatedAt();
            }
        }

        return NoticeResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .category(doc.getCategory())
                .isPinned(doc.getIsPinned())
                .noticeDetailUrl(NOTICE_DETAIL_URL_PREFIX + doc.getId())
                .createdAt(formattedDate)
                .build();
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

        // 💡 LocalDateTime -> Epoch Milli (13자리 숫자 문자열) 변환
        long millis = targetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return String.valueOf(millis);
    }

}
