package com.eum.rag.document.service.async;

import com.eum.rag.document.domain.DocumentChunk;
import com.eum.rag.document.domain.DocumentProcessingResult;
import com.eum.rag.document.domain.DocumentStatus;
import com.eum.rag.document.event.DocumentIngestionRequestedEvent;
import com.eum.rag.document.service.DocumentCleaningService;
import com.eum.rag.document.service.DocumentIndexingService;
import com.eum.rag.document.service.DocumentParsingService;
import com.eum.rag.document.service.HeaderBasedChunkingService;
import com.eum.rag.document.service.MarkdownStructuringService;
import com.eum.rag.document.repository.DocumentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAsyncPipelineHandler {

    private final DocumentRepository documentRepository;
    private final DocumentParsingService parsingService;
    private final DocumentCleaningService cleaningService;
    private final MarkdownStructuringService markdownStructuringService;
    private final HeaderBasedChunkingService chunkingService;
    private final DocumentIndexingService documentIndexingService;
    private final MeterRegistry meterRegistry;

    @Async("indexingExecutor")
    @EventListener
    public void handle(DocumentIngestionRequestedEvent event) {
        log.info("Async document pipeline started: documentId={}", event.documentId());
        Timer.Sample pipelineSample = Timer.start(meterRegistry);
        boolean success = false;

        try {
            // 1단계) 원본 바이트를 파싱하고 정제/마크다운 변환 후 헤더 기반 청킹을 수행한다.
            updateStatus(event.documentId(), DocumentStatus.PARSING, null);
            Timer.Sample parsingSample = Timer.start(meterRegistry);
            boolean parsingSuccess = false;
            String parsed;
            String cleaned;
            String markdown;
            List<DocumentChunk> chunks;
            try {
                parsed = parsingService.extractText(toMultipart(event));
                cleaned = cleaningService.clean(parsed);
                markdown = markdownStructuringService.toMarkdown(cleaned);
                chunks = chunkingService.chunk(markdown, event.documentId());
                parsingSuccess = true;
            } finally {
                parsingSample.stop(Timer.builder("rag.document.parsing.duration")
                        .tag("result", parsingSuccess ? "success" : "failure")
                        .register(meterRegistry));
            }

            documentRepository.updateContent(event.documentId(), markdown, chunks, OffsetDateTime.now());
            updateStatus(event.documentId(), DocumentStatus.PARSED, null);

            // 2단계) 색인 전에 모든 청크 임베딩을 생성한다.
            updateStatus(event.documentId(), DocumentStatus.EMBEDDING, null);
            Timer.Sample embeddingStageSample = Timer.start(meterRegistry);
            boolean embeddingSuccess = false;
            DocumentProcessingResult current;
            var chunkDocuments = java.util.Collections.<com.eum.rag.infra.elasticsearch.document.RagChunkDocument>emptyList();
            try {
                current = load(event.documentId());
                chunkDocuments = documentIndexingService.generateEmbeddings(current);
                embeddingSuccess = true;
            } finally {
                embeddingStageSample.stop(Timer.builder("rag.document.embedding.duration")
                        .tag("result", embeddingSuccess ? "success" : "failure")
                        .register(meterRegistry));
            }
            updateStatus(event.documentId(), DocumentStatus.EMBEDDED, null);

            // 3단계) 버전 기반 재색인을 수행하고 active 버전을 원자적으로 전환한다.
            updateStatus(event.documentId(), DocumentStatus.INDEXING, null);
            Timer.Sample indexingSample = Timer.start(meterRegistry);
            boolean indexingSuccess = false;
            try {
                documentIndexingService.reindexWithVersion(event.documentId(), chunkDocuments);
                indexingSuccess = true;
            } finally {
                indexingSample.stop(Timer.builder("rag.document.indexing.duration")
                        .tag("result", indexingSuccess ? "success" : "failure")
                        .register(meterRegistry));
            }
            updateStatus(event.documentId(), DocumentStatus.PROCESSED, null);

            success = true;
            log.info("Async document pipeline completed: documentId={}, chunkCount={}", event.documentId(), chunks.size());
        } catch (Exception exception) {
            // 비동기 예외는 전용 핸들러에서 격리 처리되며, 여기서는 실패 메트릭만 증가시킨다.
            meterRegistry.counter("rag.document.pipeline.failures").increment();
            throw exception;
        } finally {
            pipelineSample.stop(Timer.builder("rag.document.pipeline.duration")
                    .tag("result", success ? "success" : "failure")
                    .register(meterRegistry));
        }
    }

    private void updateStatus(String documentId, DocumentStatus status, String errorMessage) {
        documentRepository.updateStatus(documentId, status, errorMessage, OffsetDateTime.now());
    }

    private DocumentProcessingResult load(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found for async pipeline: " + documentId));
    }

    private MultipartFile toMultipart(DocumentIngestionRequestedEvent event) {
        return new RawBytesMultipartFile(event.filename(), event.fileContent());
    }
}
