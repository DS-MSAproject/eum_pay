package com.eum.rag.document.repository;

import com.eum.rag.document.domain.DocumentProcessingResult;
import com.eum.rag.document.domain.DocumentStatus;
import com.eum.rag.document.domain.DocumentChunk;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    DocumentProcessingResult save(DocumentProcessingResult result);

    Optional<DocumentProcessingResult> findById(String documentId);

    Optional<DocumentProcessingResult> updateStatus(String documentId, DocumentStatus status, String errorMessage, OffsetDateTime updatedAt);

    Optional<DocumentProcessingResult> updateContent(String documentId, String markdown, List<DocumentChunk> chunks, OffsetDateTime updatedAt);
}
