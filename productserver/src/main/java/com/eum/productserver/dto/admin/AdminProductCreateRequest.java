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
    private String imageUrl;
    private String tags;
    private String keywords;
    private Long deliveryFee;
    private String deliveryMethod;
    private String allergens;
    private String ingredients;

    /** 옵션당 초기 재고 수량 (기본값 0) */
    private int initialStock;

    private List<OptionDto> options;

    @Getter
    @Setter
    public static class OptionDto {
        private String optionName;
        private Long extraPrice;
    }
}
