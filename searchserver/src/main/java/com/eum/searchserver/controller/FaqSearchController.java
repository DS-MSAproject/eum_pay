package com.eum.searchserver.controller;

import com.eum.searchserver.dto.request.FaqSearchCondition;
import com.eum.searchserver.dto.response.FaqDetailResponse;
import com.eum.searchserver.dto.response.FaqResponse;
import com.eum.searchserver.dto.response.SearchPageResponse;
import com.eum.searchserver.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/faq")
@RequiredArgsConstructor
public class FaqSearchController {

    private final FaqService faqService;

    @GetMapping
    public Mono<SearchPageResponse<FaqResponse>> search(FaqSearchCondition condition) {
        return faqService.searchFaqs(condition);
    }

    @GetMapping("/{faqId}")
    public Mono<FaqDetailResponse> detail(@PathVariable Long faqId) {
        return faqService.getFaqDetail(faqId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ not found: " + faqId)));
    }
}
