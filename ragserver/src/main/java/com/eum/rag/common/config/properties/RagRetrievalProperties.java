package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval")
public record RagRetrievalProperties(
        int topK,
        int candidateMultiplier,
        int rrfK,
        String indexName
) {
}
