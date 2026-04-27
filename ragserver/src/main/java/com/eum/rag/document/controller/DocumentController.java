package com.eum.rag.document.controller;

import com.eum.rag.common.response.ApiResponse;
import com.eum.rag.document.dto.request.DocumentUploadRequest;
import com.eum.rag.document.dto.response.ChunkPreviewResponse;
import com.eum.rag.document.dto.response.DocumentStatusResponse;
import com.eum.rag.document.dto.response.DocumentUploadResponse;
import com.eum.rag.document.service.DocumentCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rag/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentCommandService documentCommandService;

    // 업로드 응답은 즉시 PENDING으로 반환하고, 실제 파싱/색인은 비동기로 진행한다.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "document_id", required = false) String documentId,
            @RequestParam(value = "category", required = false) String category
    ) {
        DocumentUploadRequest request = new DocumentUploadRequest(file, documentId, category);
        return ApiResponse.success(documentCommandService.upload(request));
    }

    // UI에서 processed/failed가 될 때까지 이 엔드포인트를 폴링한다.
    @GetMapping("/{documentId}")
    public ApiResponse<DocumentStatusResponse> getStatus(@PathVariable String documentId) {
        return ApiResponse.success(documentCommandService.getStatus(documentId));
    }

    // 운영/디버깅용 청크 미리보기 엔드포인트.
    @GetMapping("/{documentId}/chunks")
    public ApiResponse<ChunkPreviewResponse> getChunks(@PathVariable String documentId) {
        return ApiResponse.success(documentCommandService.getChunkPreview(documentId));
    }
}
