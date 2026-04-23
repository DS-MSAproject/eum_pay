package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.ai")
public record RagAiProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimension,
        int connectTimeoutMs,
        int readTimeoutMs,
        int retryMaxAttempts
) {
}
