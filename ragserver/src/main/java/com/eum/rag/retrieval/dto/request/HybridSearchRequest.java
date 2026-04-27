package com.eum.rag.retrieval.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 검색 요청 DTO.
 * topK는 선택값이며 null 또는 비정상 값이면 서버 기본값을 사용한다.
 */
public record HybridSearchRequest(
        @NotBlank String question,
        Integer topK
) {
}
