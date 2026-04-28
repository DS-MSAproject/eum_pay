package com.eum.rag.document.service;

import com.eum.rag.common.exception.BusinessException;
import com.eum.rag.common.exception.ErrorCode;
import com.eum.rag.common.util.IdGenerator;
import com.eum.rag.document.domain.DocumentCategory;
import com.eum.rag.document.domain.DocumentProcessingResult;
import com.eum.rag.document.domain.DocumentStatus;
import com.eum.rag.document.dto.request.DocumentUploadRequest;
import com.eum.rag.document.dto.response.ChunkPreviewResponse;
import com.eum.rag.document.dto.response.DocumentStatusResponse;
import com.eum.rag.document.dto.response.DocumentUploadResponse;
import com.eum.rag.document.event.DocumentIngestionRequestedEvent;
import com.eum.rag.document.repository.DocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCommandService {

    private final Tika tika = new Tika();
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DocumentRepository documentRepository;

    public DocumentUploadResponse upload(DocumentUploadRequest request) {
        MultipartFile file = request.file();
        validateFile(file);

        String documentId = resolveDocumentId(request.documentId());
        DocumentCategory category = DocumentCategory.from(request.category());

        log.info("Document upload requested: documentId={}, filename={}, category={}", documentId, file.getOriginalFilename(), category);

        try {
            OffsetDateTime startedAt = OffsetDateTime.now();
            documentRepository.save(new DocumentProcessingResult(
                    documentId,
                    file.getOriginalFilename(),
                    category,
                    DocumentStatus.PENDING,
                    null,
                    null,
                    "",
                    List.of(),
                    startedAt,
                    startedAt
            ));

            applicationEventPublisher.publishEvent(new DocumentIngestionRequestedEvent(
                    documentId,
                    file.getOriginalFilename(),
                    category,
                    file.getBytes()
            ));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Failed to read uploaded file.");
        }

        return new DocumentUploadResponse(
                documentId,
                file.getOriginalFilename(),
                category.name(),
                DocumentStatus.PENDING.name().toLowerCase(Locale.ROOT),
                0,
                "Document accepted. Parsing and indexing started asynchronously."
        );
    }

    public DocumentStatusResponse getStatus(String documentId) {
        DocumentProcessingResult result = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found."));

        return new DocumentStatusResponse(
                result.documentId(),
                result.filename(),
                result.category().name(),
                result.status().name().toLowerCase(Locale.ROOT),
                result.errorMessage(),
                result.chunks().size(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    public ChunkPreviewResponse getChunkPreview(String documentId) {
        DocumentProcessingResult result = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found."));

        List<ChunkPreviewResponse.ChunkItem> items = result.chunks().stream()
                .map(chunk -> new ChunkPreviewResponse.ChunkItem(
                        chunk.chunkIndex(),
                        chunk.chunkId(),
                        chunk.header(),
                        chunk.tokenCount(),
                        chunk.content()
                ))
                .toList();

        return new ChunkPreviewResponse(documentId, items);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File is required.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isSupportedExtension(originalFilename)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String contentType = file.getContentType();
        if (contentType != null && !isSupportedMimeType(originalFilename, contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String detectedMimeType = detectMimeType(file, originalFilename);
        if (!isSupportedMimeType(originalFilename, detectedMimeType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private boolean isSupportedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt");
    }

    private boolean isSupportedMimeType(String filename, String contentType) {
        String lower = filename.toLowerCase(Locale.ROOT);
        String normalized = contentType.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".pdf")) {
            return normalized.equals("application/pdf");
        }
        if (lower.endsWith(".docx")) {
            return normalized.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (lower.endsWith(".txt")) {
            return normalized.startsWith("text/plain");
        }
        return false;
    }

    private String detectMimeType(MultipartFile file, String filename) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, filename);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Failed to inspect uploaded file type.");
        }
    }

    private String resolveDocumentId(String requestedDocumentId) {
        if (requestedDocumentId == null || requestedDocumentId.isBlank()) {
            return IdGenerator.generateId();
        }
        return requestedDocumentId.trim();
    }
}
