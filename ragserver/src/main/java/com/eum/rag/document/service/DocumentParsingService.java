package com.eum.rag.document.service;

import com.eum.rag.common.exception.BusinessException;
import com.eum.rag.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class DocumentParsingService {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".txt")) {
            return extractTxt(file);
        }

        try (InputStream inputStream = file.getInputStream()) {
            String extracted = tika.parseToString(inputStream);
            if (extracted == null || extracted.isBlank()) {
                throw new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, "Extracted text is empty.");
            }
            return extracted;
        } catch (IOException | TikaException exception) {
            log.error("Failed to parse document: filename={}", file.getOriginalFilename(), exception);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, "Failed to parse the uploaded document.");
        }
    }

    private String extractTxt(MultipartFile file) {
        try {
            String extracted = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            if (extracted.isBlank()) {
                throw new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, "Extracted text is empty.");
            }
            return extracted;
        } catch (IOException exception) {
            log.error("Failed to parse txt document: filename={}", file.getOriginalFilename(), exception);
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, "Failed to parse the uploaded document.");
        }
    }
}
