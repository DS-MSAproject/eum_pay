package com.eum.productserver.service;

import com.eum.productserver.dto.request.category.CategorySaveDto;
import com.eum.productserver.dto.request.category.CategoryUpdateDto;
import com.eum.productserver.dto.response.ResCategoryDto;
import com.eum.productserver.dto.response.ResCategoryListDto;
import com.eum.productserver.entity.Category;
import com.eum.productserver.repository.CategoryRepository;
import com.eum.productserver.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // 전체 카테고리 목록 조회
    public List<ResCategoryListDto> findAll() {
        // 홈페이지 카테고리 트리 조회용입니다.
        // 대분류와 하위 카테고리를 fetch join으로 한 번에 가져와 children 접근 시 N+1 쿼리를 막습니다.
        return categoryRepository.findAllWithChildren().stream()
                .map(ResCategoryListDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 카테고리 저장
    public ResCategoryDto save(CategorySaveDto saveDto) {
        // 1. 이미 같은 이름의 카테고리가 있는지 확인
        if (categoryRepository.existsByCategoryName(saveDto.getCategoryName())) {
            // 이미 있다면 저장하지 않고 기존 데이터를 찾아 반환하거나 null 등을 반환
            Category existionCategory = categoryRepository.findByCategoryName(saveDto.getCategoryName())
                    .orElseThrow(() -> new RuntimeException("카테고리 조회 중 오류 발생"));
            return ResCategoryDto.fromEntity(existionCategory);
        }
        // 2. 없을 때만 새로 저장
        Category category = CategorySaveDto.ofEntity(saveDto);

        if (saveDto.getParentId() != null) {
            Category parent = categoryRepository.findById(saveDto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("부모 카테고리가 존재하지 않습니다."));
            category.setParent(parent);
        }

        Category savedCategory = categoryRepository.save(category);

        return ResCategoryDto.fromEntity(savedCategory);
    }

    // 카테고리 수정
    public ResCategoryDto update(CategoryUpdateDto updateDto) {
        Category category = categoryRepository.findById(updateDto.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));

                // 엔티티의 updateName 메서드를 활용 (이름만 수정)
        category.updateCategoryName(updateDto.getCategoryName());

        // 순서 변경도 가능하게
        if (updateDto.getDisplayOrder() != null) {
            category.updateDisplayOrder(updateDto.getDisplayOrder());
        }

        return ResCategoryDto.fromEntity(category);
    }

    public void delete(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 카테고리가 없습니다."));

        // (1) 하위 카테고리가 있는지 확인 (자식이 있으면 삭제 불가)
        if (!category.getChildren().isEmpty()) {
            throw new RuntimeException("하위 카테고리가 존재하여 삭제할 수 없습니다.");
        }

        // (2) 해당 카테고리(혹은 하위 포함)를 사용하는 상품이 있는지 확인
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new RuntimeException("이 카테고리에 등록된 상품이 있어 삭제할 수 없습니다.");
        }

        categoryRepository.delete(category);
    }
}
