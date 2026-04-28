package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.commerce")
public record RagCommerceProperties(
        String searchBaseUrl,
        int bestsellerSize
) {
}
