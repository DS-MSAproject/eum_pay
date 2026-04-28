package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.document")
public record RagDocumentProperties(
        int chunkSize,
        int chunkMinSize,
        int chunkMaxSize,
        double chunkOverlapRatio,
        int metadataTtlDays,
        int reindexLockWaitSeconds,
        int reindexLockTtlSeconds
) {
}
