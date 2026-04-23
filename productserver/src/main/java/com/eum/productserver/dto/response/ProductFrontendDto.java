package com.eum.productserver.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFrontendDto {

    private String imageUrl;
    private Long productId;
    private String productName;
    private Long price;
    private List<ProductFrontendOptionDto> options;
}
