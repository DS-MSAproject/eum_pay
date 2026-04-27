package com.eum.rag.document.service;

import com.eum.rag.common.config.properties.RagDocumentProperties;
import com.eum.rag.common.exception.BusinessException;
import com.eum.rag.common.exception.ErrorCode;
import com.eum.rag.document.domain.DocumentChunk;
import com.eum.rag.document.domain.DocumentProcessingResult;
import com.eum.rag.document.lock.DocumentReindexLockService;
import com.eum.rag.embedding.service.EmbeddingService;
import com.eum.rag.infra.elasticsearch.document.RagChunkDocument;
import com.eum.rag.infra.elasticsearch.repository.RagChunkSearchRepository;
import com.eum.rag.infra.elasticsearch.repository.RagIndexInitializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {

    private final RagIndexInitializer ragIndexInitializer;
    private final RagChunkSearchRepository ragChunkSearchRepository;
    private final EmbeddingService embeddingService;
    private final DocumentReindexLockService documentReindexLockService;
    private final RagDocumentProperties ragDocumentProperties;

    public List<RagChunkDocument> generateEmbeddings(DocumentProcessingResult result) {
        try {
            ragIndexInitializer.initialize();

            List<RagChunkDocument> chunkDocuments = new ArrayList<>();
            for (DocumentChunk chunk : result.chunks()) {
                List<Float> embedding = embeddingService.generateEmbedding(chunk.toSourceText());
                chunkDocuments.add(RagChunkDocument.builder()
                        .chunkId(chunk.chunkId())
                        .documentId(result.documentId())
                        .filename(result.filename())
                        .category(result.category().name())
                        .version(0)
                        .chunkIndex(chunk.chunkIndex())
                        .text(chunk.toSourceText())
                        .embedding(embedding)
                        .active(false)
                        .createdAt(chunk.createdAt())
                        .build());
            }
            return chunkDocuments;
        } catch (Exception exception) {
            log.error("Failed to generate chunk embeddings: documentId={}", result.documentId(), exception);
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR, "Failed to generate chunk embeddings.");
        }
    }

    @Transactional
    public void reindexWithVersion(String documentId, List<RagChunkDocument> chunkDocuments) {
        // 문서 단위로 버전 전환을 직렬화해, 동시 업로드 시 active 상태가 섞이지 않도록 보장한다.
        String lockToken = documentReindexLockService.acquire(
                documentId,
                Duration.ofSeconds(ragDocumentProperties.reindexLockWaitSeconds()),
                Duration.ofSeconds(ragDocumentProperties.reindexLockTtlSeconds())
        );
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.INDEXING_ERROR, "Could not acquire distributed lock for reindexing.");
        }

        int nextVersion = -1;
        List<RagChunkDocument> oldActiveSnapshot = List.of();
        try {
            nextVersion = ragChunkSearchRepository.findNextVersion(documentId);
            final int workingVersion = nextVersion;

            List<RagChunkDocument> staged = chunkDocuments.stream()
                    .map(chunk -> copy(chunk, workingVersion, false))
                    .toList();

            // 신규 버전 청크를 inactive로 먼저 적재한 뒤, 스테이징 개수 무결성을 검증한다.
            ragChunkSearchRepository.saveAll(staged, true);

            List<RagChunkDocument> newVersionDocs = ragChunkSearchRepository.findByDocumentIdAndVersion(documentId, nextVersion);
            if (newVersionDocs.size() != staged.size()) {
                throw new BusinessException(ErrorCode.INDEXING_ERROR, "Staged chunk count mismatch during version switch.");
            }

            oldActiveSnapshot = ragChunkSearchRepository.findByDocumentIdAndActive(documentId, true).stream()
                    .filter(chunk -> chunk.getVersion() == null || chunk.getVersion() != workingVersion)
                    .toList();

            List<RagChunkDocument> activateNewVersion = newVersionDocs.stream()
                    .map(chunk -> copy(chunk, chunk.getVersion(), true))
                    .toList();
            ragChunkSearchRepository.saveAll(activateNewVersion, false);

            // 신규 버전이 완전히 적재/활성화된 뒤에만 이전 active 버전을 비활성화한다.
            List<RagChunkDocument> deactivateOldVersion = oldActiveSnapshot.stream()
                    .map(chunk -> copy(chunk, chunk.getVersion(), false))
                    .toList();
            ragChunkSearchRepository.saveAll(deactivateOldVersion, false);
            ragChunkSearchRepository.refreshIndex();

            log.info("Reindex version switch completed: documentId={}, version={}, chunks={}", documentId, nextVersion, newVersionDocs.size());
        } catch (Exception exception) {
            compensate(documentId, nextVersion, oldActiveSnapshot);
            log.error("Failed to reindex chunk documents: documentId={}", documentId, exception);
            throw new BusinessException(ErrorCode.INDEXING_ERROR, "Failed to index document chunks.");
        } finally {
            documentReindexLockService.release(documentId, lockToken);
        }
    }

    private void compensate(String documentId, int failedVersion, List<RagChunkDocument> oldActiveSnapshot) {
        try {
            // 실패 버전의 부분 적재 데이터를 정리해 버전 혼합 노출을 방지한다.
            if (failedVersion > 0) {
                List<RagChunkDocument> failedVersionDocs = ragChunkSearchRepository.findByDocumentIdAndVersion(documentId, failedVersion);
                List<RagChunkDocument> deactivateFailedVersion = failedVersionDocs.stream()
                        .map(chunk -> copy(chunk, chunk.getVersion(), false))
                        .toList();
                ragChunkSearchRepository.saveAll(deactivateFailedVersion, false);
                ragChunkSearchRepository.deleteByDocumentIdAndVersion(documentId, failedVersion);
            }

            // 이전 active 스냅샷을 복원해 검색 일관성을 유지한다.
            if (oldActiveSnapshot != null && !oldActiveSnapshot.isEmpty()) {
                List<RagChunkDocument> restoreOldActive = oldActiveSnapshot.stream()
                        .map(chunk -> copy(chunk, chunk.getVersion(), true))
                        .toList();
                ragChunkSearchRepository.saveAll(restoreOldActive, false);
            }
            ragChunkSearchRepository.refreshIndex();
        } catch (Exception compensationException) {
            log.error("Compensation failed for document reindex: documentId={}, version={}", documentId, failedVersion, compensationException);
        }
    }

    private RagChunkDocument copy(RagChunkDocument source, Integer version, boolean active) {
        return RagChunkDocument.builder()
                .chunkId(source.getChunkId())
                .documentId(source.getDocumentId())
                .filename(source.getFilename())
                .category(source.getCategory())
                .version(version)
                .chunkIndex(source.getChunkIndex())
                .text(source.getText())
                .embedding(source.getEmbedding())
                .active(active)
                .createdAt(source.getCreatedAt())
                .build();
    }
}
