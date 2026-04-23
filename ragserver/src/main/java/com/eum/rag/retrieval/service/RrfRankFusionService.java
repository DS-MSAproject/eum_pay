package com.eum.rag.retrieval.service;

import com.eum.rag.infra.elasticsearch.document.RagChunkDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RrfRankFusionService {

    public List<RankedChunk> fuse(List<RagChunkDocument> lexical,
                                  List<RagChunkDocument> semantic,
                                  int topK,
                                  int rrfK) {
        Map<String, RankedChunk> scoreBoard = new LinkedHashMap<>();
        addWithRrf(scoreBoard, lexical, rrfK, "lexical");
        addWithRrf(scoreBoard, semantic, rrfK, "semantic");

        return scoreBoard.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }

    private void addWithRrf(Map<String, RankedChunk> scoreBoard,
                            List<RagChunkDocument> ranked,
                            int rrfK,
                            String source) {
        for (int i = 0; i < ranked.size(); i++) {
            RagChunkDocument chunk = ranked.get(i);
            int rank = i + 1;
            double contribution = 1.0d / (rrfK + rank);

            RankedChunk existing = scoreBoard.get(chunk.getChunkId());
            if (existing == null) {
                scoreBoard.put(chunk.getChunkId(), RankedChunk.from(chunk, contribution, List.of(source)));
            } else {
                List<String> mergedSignals = new ArrayList<>(existing.signals());
                if (!mergedSignals.contains(source)) {
                    mergedSignals.add(source);
                }
                scoreBoard.put(chunk.getChunkId(), existing.with(existing.score() + contribution, mergedSignals));
            }
        }
    }

    public record RankedChunk(
            String chunkId,
            String documentId,
            String filename,
            String category,
            Integer chunkIndex,
            String text,
            double score,
            List<String> signals
    ) {
        static RankedChunk from(RagChunkDocument chunk, double score, List<String> signals) {
            return new RankedChunk(
                    chunk.getChunkId(),
                    chunk.getDocumentId(),
                    chunk.getFilename(),
                    chunk.getCategory(),
                    chunk.getChunkIndex(),
                    chunk.getText(),
                    score,
                    signals
            );
        }

        RankedChunk with(double newScore, List<String> mergedSignals) {
            return new RankedChunk(chunkId, documentId, filename, category, chunkIndex, text, newScore, mergedSignals);
        }
    }
}
