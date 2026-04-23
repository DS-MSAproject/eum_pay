package com.eum.productserver.dto.request.item.update;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@NoArgsConstructor
public class ProductOptionUpdateDto {

    private Long optionId; // 기존 옵션 수정 시 필수

    @Nullable
    private String optionName; // 옵션 명칭 수정
    private Long extraPrice;   // 추가 금액 수정
}
