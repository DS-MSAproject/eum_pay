package com.eum.productserver.dto.request.category;

import com.eum.productserver.entity.Category;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySaveDto {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    private String categoryName;

    private Long parentId; // 부모 카테고리 ID (대분류 등록 시에는 null)

    @Builder.Default
    private Integer displayOrder = 0; // 노출 순서

    public static Category ofEntity(CategorySaveDto dto) {
        return Category.builder()
                .categoryName(dto.getCategoryName())
                .displayOrder(dto.getDisplayOrder())
                .build();
    }
}
