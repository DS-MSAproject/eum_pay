package com.eum.rag.embedding.service;

import com.eum.rag.embedding.client.EmbeddingClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;

    public List<Float> generateEmbedding(String text) {
        return embeddingClient.embed(text);
    }
}
