package com.eum.searchserver.controller;

import com.eum.searchserver.dto.request.ReviewSearchCondition;
import com.eum.searchserver.dto.response.ReviewHeaderResponse;
import com.eum.searchserver.dto.response.ReviewSearchResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import com.eum.searchserver.service.ReviewSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/search/reviews")
@RequiredArgsConstructor
public class ReviewSearchController {

    private final ReviewSearchService reviewSearchService;

    @GetMapping
    public Mono<SearchPageResponse<ReviewSearchResponse>> searchReviews(ReviewSearchCondition condition) {
        return reviewSearchService.searchReviews(condition);
    }

    @GetMapping("/header")
    public Mono<ReviewHeaderResponse> reviewHeader(ReviewSearchCondition condition) {
        return reviewSearchService.getReviewHeader(condition);
    }
}
