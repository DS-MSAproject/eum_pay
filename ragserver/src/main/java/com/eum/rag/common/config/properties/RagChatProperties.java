package com.eum.rag.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.chat")
public record RagChatProperties(
        int maxSources,
        int rewriteShortQuestionLength,
        int llmTemperature
) {
}
