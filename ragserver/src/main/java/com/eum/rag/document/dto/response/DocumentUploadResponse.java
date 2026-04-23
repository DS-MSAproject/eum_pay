package com.eum.rag.document.dto.response;

/**
 * 업로드 즉시 응답 DTO.
 * 이후 처리 상태 변화는 상태 조회(폴링) 엔드포인트로 추적한다.
 */
public record DocumentUploadResponse(
        String documentId,
        String filename,
        String category,
        String status,
        int chunkCount,
        String message
) {
}
