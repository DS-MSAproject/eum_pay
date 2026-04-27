package com.eum.rag.document.repository;

import com.eum.rag.document.domain.DocumentChunk;
import com.eum.rag.document.domain.DocumentProcessingResult;
import com.eum.rag.document.domain.DocumentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("inmemory-docrepo")
public class InMemoryDocumentRepository implements DocumentRepository {

    private final Map<String, DocumentProcessingResult> storage = new ConcurrentHashMap<>();

    @Override
    public DocumentProcessingResult save(DocumentProcessingResult result) {
        storage.put(result.documentId(), result);
        return result;
    }

    @Override
    public Optional<DocumentProcessingResult> findById(String documentId) {
        return Optional.ofNullable(storage.get(documentId));
    }

    @Override
    public Optional<DocumentProcessingResult> updateStatus(String documentId, DocumentStatus status, String errorMessage, OffsetDateTime updatedAt) {
        return Optional.ofNullable(storage.computeIfPresent(documentId, (id, existing) ->
                existing.withStatus(status, errorMessage, updatedAt)));
    }

    @Override
    public Optional<DocumentProcessingResult> updateContent(String documentId, String markdown, List<DocumentChunk> chunks, OffsetDateTime updatedAt) {
        return Optional.ofNullable(storage.computeIfPresent(documentId, (id, existing) ->
                existing.withContent(markdown, chunks, updatedAt)));
    }
}
