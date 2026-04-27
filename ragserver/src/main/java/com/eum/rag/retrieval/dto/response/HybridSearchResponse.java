package com.eum.rag.retrieval.dto.response;

import java.util.List;

/**
 * 하이브리드 검색 결과 응답 DTO.
 */
public record HybridSearchResponse(
        String query,
        int topK,
        List<RetrievedChunk> results
) {
    // score는 RRF로 융합된 최종 랭킹 점수이며, BM25/코사인 원점수와 다를 수 있다.
    public record RetrievedChunk(
            String chunkId,
            String documentId,
            String filename,
            String category,
            Integer chunkIndex,
            String text,
            double score
    ) {
    }
}
