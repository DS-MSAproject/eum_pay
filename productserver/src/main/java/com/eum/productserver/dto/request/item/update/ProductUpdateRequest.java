package com.eum.productserver.dto.request.item.update;

import com.eum.productserver.dto.request.item.save.ImageFileSaveDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * -Request
 * 판매자 상품 수정 정보들
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductUpdateRequest {

    @NotNull(message = "카테고리 선택은 필수입니다.")
    private Long categoryId;

    @Valid
    @NotNull(message = "수정 정보가 누락되었습니다.")
    private ProductUpdateDto productUpdateDto;
    private List<ImageFileSaveDto> imageFileSaveDtoList = new ArrayList<>();
    private List<ImageFileSaveDto> detailImageFileSaveDtoList = new ArrayList<>();
}
