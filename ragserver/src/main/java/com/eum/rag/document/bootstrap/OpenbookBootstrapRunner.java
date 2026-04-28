package com.eum.rag.document.bootstrap;

import com.eum.rag.document.dto.request.DocumentUploadRequest;
import com.eum.rag.document.lock.DocumentReindexLockService;
import com.eum.rag.document.repository.DocumentRepository;
import com.eum.rag.document.service.DocumentCommandService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenbookBootstrapRunner implements ApplicationRunner {

    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MILLIS = 30_000L;

    private final DocumentCommandService documentCommandService;
    private final DocumentRepository documentRepository;
    private final DocumentReindexLockService lockService;

    @Value("${rag.document.bootstrap.enabled:true}")
    private boolean enabled;

    @Value("${rag.document.bootstrap.document-id:petfood-openbook}")
    private String documentId;

    @Value("${rag.document.bootstrap.category:NOTICE}")
    private String category;

    @Value("${rag.document.bootstrap.file-path:ragserver/docs/petfood-openbook-upload-ready.pdf}")
    private String filePath;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Openbook bootstrap skipped: disabled by config.");
            return;
        }

        Path resolved = resolvePath(filePath);
        if (resolved == null) {
            log.warn("Openbook bootstrap skipped: file not found. configuredPath={}", filePath);
            return;
        }

        CompletableFuture.runAsync(() -> uploadWithRetry(resolved));
    }

    private void uploadWithRetry(Path resolved) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (tryUploadOnce(resolved)) {
                    return;
                }
                return;
            } catch (Exception exception) {
                if (attempt == MAX_RETRIES) {
                    log.error("Openbook bootstrap failed after retries: documentId={}, path={}", documentId, resolved.toAbsolutePath(), exception);
                    return;
                }
                log.warn("Openbook bootstrap attempt {}/{} failed. retry in {} ms. documentId={}, reason={}",
                        attempt, MAX_RETRIES, RETRY_DELAY_MILLIS, documentId, exception.getMessage());
                sleepQuietly(RETRY_DELAY_MILLIS);
            }
        }
    }

    private boolean tryUploadOnce(Path resolved) throws Exception {
        byte[] bytes = Files.readAllBytes(resolved);
        String sourceHash = sha256Hex(bytes);

        String lockToken = lockService.acquire(
                documentId,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60)
        );
        if (lockToken == null) {
            throw new IllegalStateException("bootstrap lock not acquired");
        }

        try {
            var existing = documentRepository.findById(documentId);
            if (existing.isPresent() && sourceHash.equals(existing.get().sourceHash())) {
                log.info("Openbook bootstrap skipped: unchanged source hash. documentId={}, hash={}", documentId, sourceHash);
                return false;
            }

            MultipartFile multipartFile = new BootstrapMultipartFile(resolved.getFileName().toString(), bytes);
            documentCommandService.upload(new DocumentUploadRequest(multipartFile, documentId, category));

            var updated = documentRepository.findById(documentId);
            if (updated.isPresent()) {
                var current = updated.get();
                documentRepository.save(new com.eum.rag.document.domain.DocumentProcessingResult(
                        current.documentId(),
                        current.filename(),
                        current.category(),
                        current.status(),
                        current.errorMessage(),
                        sourceHash,
                        current.markdown(),
                        current.chunks(),
                        current.createdAt(),
                        OffsetDateTime.now()
                ));
            }

            log.info("Openbook bootstrap uploaded: documentId={}, path={}, hash={}", documentId, resolved.toAbsolutePath(), sourceHash);
            return true;
        } finally {
            lockService.release(documentId, lockToken);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash bootstrap file", exception);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private Path resolvePath(String configuredPath) {
        List<Path> candidates = List.of(
                Paths.get(configuredPath),
                Paths.get("docs", "petfood-openbook-upload-ready.pdf"),
                Paths.get("ragserver", "docs", "petfood-openbook-upload-ready.pdf")
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static final class BootstrapMultipartFile implements MultipartFile {
        private final String filename;
        private final byte[] content;

        private BootstrapMultipartFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content == null ? new byte[0] : content;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return "application/pdf";
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            throw new UnsupportedOperationException("transferTo is not supported");
        }
    }
}
