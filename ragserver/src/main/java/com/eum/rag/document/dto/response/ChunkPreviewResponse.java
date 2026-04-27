package com.eum.rag.document.dto.response;

import java.util.List;

/**
 * 파싱/청킹 결과를 확인하기 위한 운영/디버깅용 응답 DTO.
 */
public record ChunkPreviewResponse(
        String documentId,
        List<ChunkItem> chunks
) {
    // tokenCount는 청킹 품질 점검용 추정치이며, 과금 기준 토큰 수와는 다를 수 있다.
    public record ChunkItem(
            int chunkIndex,
            String chunkId,
            String header,
            int tokenCount,
            String content
    ) {
    }
}
