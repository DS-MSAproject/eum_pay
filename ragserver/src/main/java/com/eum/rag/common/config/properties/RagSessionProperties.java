package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.session")
public record RagSessionProperties(
        int ttlHours,
        int maxMessages
) {
}
