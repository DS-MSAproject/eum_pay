package com.eum.rag.retrieval.service;

import com.eum.rag.common.config.properties.RagRetrievalProperties;
import com.eum.rag.embedding.service.EmbeddingService;
import com.eum.rag.infra.elasticsearch.document.RagChunkDocument;
import com.eum.rag.infra.elasticsearch.repository.RagChunkSearchRepository;
import com.eum.rag.retrieval.dto.request.HybridSearchRequest;
import com.eum.rag.retrieval.dto.response.HybridSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final RagChunkSearchRepository ragChunkSearchRepository;
    private final EmbeddingService embeddingService;
    private final RagRetrievalProperties ragRetrievalProperties;
    private final RrfRankFusionService rrfRankFusionService;

    public HybridSearchResponse search(HybridSearchRequest request) {
        int topK = request.topK() == null || request.topK() < 1
                ? ragRetrievalProperties.topK()
                : request.topK();

        // 먼저 넓은 후보군을 가져온 뒤, RRF 융합 결과에서 최종 top-k를 잘라낸다.
        int candidateSize = Math.max(topK * ragRetrievalProperties.candidateMultiplier(), topK);

        List<RagChunkDocument> lexical = ragChunkSearchRepository.lexicalSearch(request.question(), candidateSize);

        List<Float> queryEmbedding = embeddingService.generateEmbedding(request.question());
        List<RagChunkDocument> semantic = ragChunkSearchRepository.semanticSearch(queryEmbedding, candidateSize);

        // RRF 기법으로 lexical 정밀도와 semantic 재현율을 균형 있게 합친다.
        List<RrfRankFusionService.RankedChunk> fused = rrfRankFusionService.fuse(
                lexical,
                semantic,
                topK,
                ragRetrievalProperties.rrfK()
        );

        List<HybridSearchResponse.RetrievedChunk> result = fused.stream()
                .map(chunk -> new HybridSearchResponse.RetrievedChunk(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.filename(),
                        chunk.category(),
                        chunk.chunkIndex(),
                        chunk.text(),
                        chunk.score()
                ))
                .toList();

        return new HybridSearchResponse(request.question(), topK, result);
    }
}
