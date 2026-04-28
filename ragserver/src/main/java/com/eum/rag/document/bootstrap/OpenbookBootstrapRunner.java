package com.eum.rag.document.bootstrap;

import com.eum.rag.document.dto.request.DocumentUploadRequest;
import com.eum.rag.document.repository.DocumentRepository;
import com.eum.rag.document.service.DocumentCommandService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

    private final DocumentCommandService documentCommandService;
    private final DocumentRepository documentRepository;

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

        if (documentRepository.findById(documentId).isPresent()) {
            log.info("Openbook bootstrap skipped: documentId={} already exists.", documentId);
            return;
        }

        Path resolved = resolvePath(filePath);
        if (resolved == null) {
            log.warn("Openbook bootstrap skipped: file not found. configuredPath={}", filePath);
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(resolved);
            MultipartFile multipartFile = new BootstrapMultipartFile(resolved.getFileName().toString(), bytes);
            documentCommandService.upload(new DocumentUploadRequest(multipartFile, documentId, category));
            log.info("Openbook bootstrap uploaded: documentId={}, path={}", documentId, resolved.toAbsolutePath());
        } catch (Exception exception) {
            log.error("Openbook bootstrap failed: documentId={}, path={}", documentId, resolved.toAbsolutePath(), exception);
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
