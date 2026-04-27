package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Category;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * -Response
 * 카테고리 목록 정보
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResCategoryListDto {

    private Long categoryId;
    private String name;
    private Integer displayOrder;

    @Builder.Default
    private List<ResCategoryListDto> children = new ArrayList<>();

    public static ResCategoryListDto fromEntity(Category category) {
        if (category == null) return null;

        return ResCategoryListDto.builder()
                .categoryId(category.getId())
                .name(category.getCategoryName())
                .displayOrder(category.getDisplayOrder())
                .children(category.getChildren().stream()
                        .map(ResCategoryListDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
}
