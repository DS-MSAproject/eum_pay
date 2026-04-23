package com.eum.rag.document.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 요청 DTO.
 * documentId는 선택값이며, 같은 id를 재사용하면 버전 기반 재색인이 동작한다.
 */
public record DocumentUploadRequest(
        @NotNull MultipartFile file,
        String documentId,
        String category
) {
}
