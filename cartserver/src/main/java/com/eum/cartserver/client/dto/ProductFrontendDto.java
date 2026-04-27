package com.eum.cartserver.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ProductFrontendDto {

    private Long productId;
    private List<ProductFrontendOptionDto> options;
}
