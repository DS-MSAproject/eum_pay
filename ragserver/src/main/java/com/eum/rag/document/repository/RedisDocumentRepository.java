package com.eum.rag.document.repository;

import com.eum.rag.document.domain.DocumentChunk;
import com.eum.rag.document.domain.DocumentProcessingResult;
import com.eum.rag.document.domain.DocumentStatus;
import com.eum.rag.common.config.properties.RagDocumentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@RequiredArgsConstructor
public class RedisDocumentRepository implements DocumentRepository {

    private static final String PREFIX = "rag:document:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RagDocumentProperties ragDocumentProperties;

    @Override
    public DocumentProcessingResult save(DocumentProcessingResult result) {
        ValueOperations<String, Object> operations = redisTemplate.opsForValue();
        operations.set(key(result.documentId()), result, Duration.ofDays(ragDocumentProperties.metadataTtlDays()));
        return result;
    }

    @Override
    public Optional<DocumentProcessingResult> findById(String documentId) {
        Object raw = redisTemplate.opsForValue().get(key(documentId));
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof DocumentProcessingResult result) {
            return Optional.of(result);
        }
        if (raw instanceof Map<?, ?> map) {
            return Optional.of(objectMapper.convertValue(map, DocumentProcessingResult.class));
        }
        return Optional.empty();
    }

    @Override
    public Optional<DocumentProcessingResult> updateStatus(String documentId, DocumentStatus status, String errorMessage, OffsetDateTime updatedAt) {
        Optional<DocumentProcessingResult> existing = findById(documentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        DocumentProcessingResult updated = existing.get().withStatus(status, errorMessage, updatedAt);
        save(updated);
        return Optional.of(updated);
    }

    @Override
    public Optional<DocumentProcessingResult> updateContent(String documentId, String markdown, List<DocumentChunk> chunks, OffsetDateTime updatedAt) {
        Optional<DocumentProcessingResult> existing = findById(documentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        DocumentProcessingResult updated = existing.get().withContent(markdown, chunks, updatedAt);
        save(updated);
        return Optional.of(updated);
    }

    private String key(String documentId) {
        return PREFIX + documentId;
    }
}
