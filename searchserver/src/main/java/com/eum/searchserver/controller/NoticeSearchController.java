package com.eum.searchserver.controller;

import com.eum.searchserver.dto.request.NoticeSearchCondition;
import com.eum.searchserver.dto.response.NoticeResponse;
import com.eum.searchserver.dto.response.NoticeSearchResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import com.eum.searchserver.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/search/notices")
@RequiredArgsConstructor
public class NoticeSearchController {

    private final NoticeService noticeService;
    private static final String DEFAULT_MENU_TITLE = "NOTICE";

    /**
     * [최종] 공지사항 검색 및 조회
     * 개조된 SearchPageResponse의 extra 필드에 "NOTICE" 타이틀을 주입합니다.
     */
    @GetMapping
    public Mono<SearchPageResponse<NoticeResponse>> search(NoticeSearchCondition condition) {
        return noticeService.searchNotices(condition)
                .map(response -> {
                    // 💡 [개조 포인트] 메뉴 타이틀을 Map에 담아 extra로 전달
                    java.util.Map<String, Object> extra = java.util.Map.of("menuTitle", DEFAULT_MENU_TITLE);

                    return SearchPageResponse.of(
                            response.data(),
                            response.totalElements(), // 💡 필드명 변경 반영 (totalProductNumber -> totalElements)
                            response.currentPage(),
                            response.size(),
                            extra // 💡 개조된 extra 필드 활용
                    );
                });
    }
}
