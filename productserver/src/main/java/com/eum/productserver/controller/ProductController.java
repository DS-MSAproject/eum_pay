package com.eum.productserver.controller;

import com.eum.productserver.dto.request.item.save.ProductSaveRequest;
import com.eum.productserver.dto.request.item.update.ProductUpdateRequest;
import com.eum.productserver.dto.response.*;
import com.eum.productserver.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    // 1. 상품 검색 및 목록 조회 (페이징 적용)
    // 카테고리 필터링 + 페이징 구조입니다.
    // Search Server로 이관
//    @GetMapping("/products")
//    public ResponseEntity<Page<ResProductListDto>> getProducts(
//            @RequestParam(required = false) Long categoryId,
//            @PageableDefault(size = 10) Pageable pageable) {
//
//        Page<ResProductListDto> response = productService.findAll(categoryId, pageable);
//        return ResponseEntity.ok(response);
//    }

    // 2. 상품 상세 조회 (상세 DTO 반환)
    @GetMapping("/{productId}")
    public ResponseEntity<ResProductDetail> getProductDetail(@PathVariable Long productId) {
        ResProductDetail response = productService.findProductDetail(productId);
        return ResponseEntity.ok(response);
    }

//    // 3. 상품 등록
//    // files는 대표/상품 이미지 목록, detailImageFiles는 상세 설명 영역에 순서대로 노출할 이미지 목록입니다.
//    @PostMapping(value = "/seller", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<ResProductSaveDto> save(
//            @RequestPart("data") String data,
//            @RequestPart(value = "files", required = false) List<MultipartFile> files,
//            @RequestPart(value = "detailImageFiles", required = false) List<MultipartFile> detailImageFiles) {
//
//        ProductSaveRequest request = parseAndValidate(data, ProductSaveRequest.class);
//        ResProductSaveDto response = productService.save(request, files, detailImageFiles);
//
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(response);
//    }
//
//    // 4. 상품 수정
//    // detailImageFiles가 들어오면 S3 업로드 URL 목록으로 교체하고, 없으면 data의 detailImageFileSaveDtoList 값을 사용합니다.
//    @PutMapping(path="/seller/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<ResProductUpdateDto> update(
//            @PathVariable Long productId,
//            @RequestPart("data") String data,
//            @RequestPart(value = "files", required = false) List<MultipartFile> files,
//            @RequestPart(value = "detailImageFiles", required = false) List<MultipartFile> detailImageFiles) {
//
//        ProductUpdateRequest request = parseAndValidate(data, ProductUpdateRequest.class);
//        if (request.getProductUpdateDto() != null) {
//            request.getProductUpdateDto().setProductId(productId);
//        }
//
//        ResProductUpdateDto response = productService.update(request, files, detailImageFiles);
//        return ResponseEntity.ok(response);
//    }
//
//    private <T> T parseAndValidate(String rawData, Class<T> type) {
//        final T request;
//        try {
//            request = objectMapper.readValue(rawData, type);
//        } catch (JsonProcessingException ex) {
//            throw new IllegalArgumentException("상품 요청 데이터 형식이 올바르지 않습니다.");
//        }
//
//        Set<ConstraintViolation<T>> violations = validator.validate(request);
//        if (!violations.isEmpty()) {
//            throw new IllegalArgumentException(violations.iterator().next().getMessage());
//        }
//        return request;
//    }

//    // 5. 상품 삭제
//    @DeleteMapping("/seller/{productId}")
//    public ResponseEntity<Void> delete(
//            @PathVariable Long productId,
//            @RequestParam Long sellerId) { // 판매자 본인 확인용
//
//        productService.deleteProduct(productId, sellerId);
//        return ResponseEntity.noContent().build();
//    }
}
