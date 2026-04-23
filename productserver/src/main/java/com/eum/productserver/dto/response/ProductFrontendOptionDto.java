package com.eum.productserver.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFrontendOptionDto {

    private Long optionId;
    @Nullable
    private String optionName;
}
