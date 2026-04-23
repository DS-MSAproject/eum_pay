package com.eum.rag.retrieval.controller;

import com.eum.rag.common.response.ApiResponse;
import com.eum.rag.retrieval.dto.request.HybridSearchRequest;
import com.eum.rag.retrieval.dto.response.HybridSearchResponse;
import com.eum.rag.retrieval.service.HybridSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/search")
@RequiredArgsConstructor
public class RetrievalController {

    private final HybridSearchService hybridSearchService;

    // 검색 UI 엔드포인트: lexical + semantic 결과를 RRF로 합쳐 top-k를 반환한다.
    @PostMapping
    public ApiResponse<HybridSearchResponse> search(@Valid @RequestBody HybridSearchRequest request) {
        return ApiResponse.success(hybridSearchService.search(request));
    }
}
