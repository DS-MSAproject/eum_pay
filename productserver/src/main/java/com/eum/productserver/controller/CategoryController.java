package com.eum.productserver.controller;

import com.eum.productserver.dto.request.category.CategorySaveDto;
import com.eum.productserver.dto.request.category.CategoryUpdateDto;
import com.eum.productserver.dto.response.ResCategoryDto;
import com.eum.productserver.dto.response.ResCategoryListDto;
import com.eum.productserver.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // 1. 전체 카테고리 조회 (대분류-소분류 트리 구조 반환)
    @GetMapping
    public ResponseEntity<List<ResCategoryListDto>> findAll() {
        List<ResCategoryListDto> list = categoryService.findAll();
        return ResponseEntity.ok(list);
    }

//    // 2. 카테고리 저장 (대분류 혹은 소분류)
//    @PostMapping
//    public ResponseEntity<ResCategoryDto> save(
//            @RequestBody @Valid CategorySaveDto categorySaveDto) {
//
//        ResCategoryDto category = categoryService.save(categorySaveDto);
//        // 생성 시에는 보통 201 Created를 보내는 것이 표준입니다.
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(category);
//    }
//
//    // 3. 카테고리 수정
//    @PutMapping("/{categoryId}")
//    public ResponseEntity<ResCategoryDto> update(
//            @PathVariable("categoryId") Long categoryId,
//            @RequestBody @Valid CategoryUpdateDto categoryUpdateDto) {
//
//        categoryUpdateDto.setCategoryId(categoryId);
//        ResCategoryDto category = categoryService.update(categoryUpdateDto);
//        return ResponseEntity.ok(category);
//    }
//
//    // 4. 카테고리 삭제
//    @DeleteMapping("/{categoryId}")
//    public ResponseEntity<Void> delete( // 반환할 데이터가 없으면 Void가 깔끔합니다.
//                                        @PathVariable("categoryId") Long categoryId) {
//
//        categoryService.delete(categoryId);
//        return ResponseEntity.noContent().build(); // 204 No Content 반환
//    }
}
