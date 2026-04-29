package com.eum.productserver.controller;

import com.eum.productserver.dto.admin.AdminBulkUploadResult;
import com.eum.productserver.dto.admin.AdminProductCreateRequest;
import com.eum.productserver.dto.admin.AdminProductDetailResponse;
import com.eum.productserver.dto.admin.AdminProductListResponse;
import com.eum.productserver.dto.admin.AdminProductStatusRequest;
import com.eum.productserver.entity.ProductLifecycleStatus;
import com.eum.productserver.service.AdminBulkUploadService;
import com.eum.productserver.service.AdminProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;
    private final AdminBulkUploadService adminBulkUploadService;
    private final com.eum.productserver.service.ProductImageUploadService productImageUploadService;

    /**
     * GET /admin/products?page=0&size=20&lifecycleStatus=DRAFT
     * 관리자 상품 목록 조회
     */
    @GetMapping
    public ResponseEntity<Page<AdminProductListResponse>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ProductLifecycleStatus lifecycleStatus) {

        Page<AdminProductListResponse> result = adminProductService.listProducts(page, size, lifecycleStatus);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /admin/products/{productId}
     * 관리자 상품 상세 조회
     */
    @GetMapping("/{productId}")
    public ResponseEntity<AdminProductDetailResponse> getProductDetail(@PathVariable Long productId) {
        AdminProductDetailResponse response = adminProductService.getProductDetail(productId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /admin/products
     * 관리자 상품 생성
     */
    @PostMapping
    public ResponseEntity<AdminProductDetailResponse> createProduct(
            @Valid @RequestBody AdminProductCreateRequest request) {

        AdminProductDetailResponse response = adminProductService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /admin/products/{productId}
     * 관리자 상품 수정
     */
    @PutMapping("/{productId}")
    public ResponseEntity<AdminProductDetailResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductCreateRequest request) {

        AdminProductDetailResponse response = adminProductService.updateProduct(productId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /admin/products/{productId}
     * 관리자 상품 삭제
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        adminProductService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /admin/products/{productId}/status
     * 상품 라이프사이클 상태 전이
     */
    @PatchMapping("/{productId}/status")
    public ResponseEntity<AdminProductDetailResponse> transitionStatus(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductStatusRequest request) {

        AdminProductDetailResponse response = adminProductService.transitionStatus(productId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /admin/products/image-upload
     * 상품 대표 이미지 S3 업로드 → URL 반환
     */
    @PostMapping("/image-upload")
    public ResponseEntity<java.util.Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        com.eum.productserver.dto.request.item.save.ImageFileSaveDto dto =
                productImageUploadService.upload(file);
        return ResponseEntity.ok(java.util.Map.of(
                "imageUrl", dto.getImageUrl(),
                "imageKey", dto.getImageKey()));
    }

    /**
     * POST /admin/products/bulk-upload
     * CSV 파일 대량 상품 업로드
     */
    @PostMapping("/bulk-upload")
    public ResponseEntity<AdminBulkUploadResult> bulkUpload(
            @RequestParam("csvFile") MultipartFile csvFile) {

        if (csvFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        AdminBulkUploadResult result = adminBulkUploadService.uploadFromCsv(csvFile);
        return ResponseEntity.ok(result);
    }
}
