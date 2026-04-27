package com.eum.rag.document.dto.response;

import java.time.OffsetDateTime;

/**
 * 비동기 문서 처리 파이프라인 진행 상태를 반환하는 폴링 응답 DTO.
 */
public record DocumentStatusResponse(
        String documentId,
        String filename,
        String category,
        String status,
        String errorMessage,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
