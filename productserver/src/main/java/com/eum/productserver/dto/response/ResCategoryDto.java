package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Category;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * -Response
 * 카테고리 등록 정보
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResCategoryDto {

    private Long categoryId;
    private String categoryName; // name -> categoryName (엔티티와 통일)
    private Long parentId;       // 부모 카테고리 ID

    // 💡 핵심: 하위 카테고리 리스트 (Snack&Jerky 아래의 '오독오독' 등)
    private List<ResCategoryDto> children;

    public static ResCategoryDto fromEntity(Category category) {
        if (category == null) return null;

        return ResCategoryDto.builder()
                .categoryId(category.getId())
                .categoryName(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                // 💡 자식 카테고리들을 다시 DTO로 변환하여 트리를 만듭니다 (재귀 호출)
                .children(category.getChildren() != null ?
                        category.getChildren().stream()
                                .map(ResCategoryDto::fromEntity)
                                .collect(Collectors.toList()) : null)
                .build();
    }
}
