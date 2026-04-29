package com.eum.productserver.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AdminProductCreateRequest {

    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max = 100)
    private String productName;

    @NotBlank
    private String content;

    @NotNull
    @Min(0)
    private Long price;

    private String brandName;
    private String tags;
    private String keywords;
    private Long deliveryFee;
    private String deliveryMethod;
    private String allergens;
    private String ingredients;

    /** 옵션당 초기 재고 수량 (기본값 0) */
    private int initialStock;

    private List<OptionDto> options;

    /** 상품 이미지 (isMain=true 인 항목이 대표 이미지) */
    private List<ImageDto> images;

    /** 상세 설명 이미지 (displayOrder 순으로 표시) */
    private List<DetailImageDto> detailImages;

    @Getter
    @Setter
    public static class OptionDto {
        private String optionName;
        private Long extraPrice;
    }

    @Getter
    @Setter
    public static class ImageDto {
        private String imageUrl;
        private String imageKey;
        private boolean isMain;
    }

    @Getter
    @Setter
    public static class DetailImageDto {
        private String imageUrl;
        private String imageKey;
        private Integer displayOrder;
    }
}
