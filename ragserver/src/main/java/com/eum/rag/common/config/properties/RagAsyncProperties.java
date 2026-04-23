package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.async")
public record RagAsyncProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        String threadNamePrefix
) {
}
