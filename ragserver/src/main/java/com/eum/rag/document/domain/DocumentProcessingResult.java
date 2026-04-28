package com.eum.rag.document.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record DocumentProcessingResult(
        String documentId,
        String filename,
        DocumentCategory category,
        DocumentStatus status,
        String errorMessage,
        String sourceHash,
        String markdown,
        List<DocumentChunk> chunks,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public DocumentProcessingResult withStatus(DocumentStatus newStatus, String newErrorMessage, OffsetDateTime updatedAt) {
        return new DocumentProcessingResult(
                documentId,
                filename,
                category,
                newStatus,
                newErrorMessage,
                sourceHash,
                markdown,
                chunks,
                createdAt,
                updatedAt
        );
    }

    public DocumentProcessingResult withContent(String newMarkdown, List<DocumentChunk> newChunks, OffsetDateTime updatedAt) {
        return new DocumentProcessingResult(
                documentId,
                filename,
                category,
                status,
                errorMessage,
                sourceHash,
                newMarkdown,
                newChunks,
                createdAt,
                updatedAt
        );
    }
}
