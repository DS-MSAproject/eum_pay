package com.eum.rag.document.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record DocumentChunk(
        String chunkId,
        String documentId,
        int chunkIndex,
        String header,
        String content,
        int tokenCount,
        OffsetDateTime createdAt
) {
    public String toSourceText() {
        if (header == null || header.isBlank()) {
            return content;
        }
        return header + System.lineSeparator() + content;
    }

    public List<String> lines() {
        return List.of(toSourceText().split("\\R"));
    }
}
