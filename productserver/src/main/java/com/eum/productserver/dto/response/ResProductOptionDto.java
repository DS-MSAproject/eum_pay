package com.eum.productserver.dto.response;

import com.eum.productserver.entity.ProductOption;
import lombok.*;
import org.springframework.lang.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResProductOptionDto {

    private Long optionId;
    @Nullable
    private String optionName;
    private Long extraPrice;
    private Integer stockQuantity;
    private String stockStatus;

    public static ResProductOptionDto fromEntity(ProductOption option) {
        return fromEntity(option, null);
    }

    public static ResProductOptionDto fromEntity(ProductOption option, ProductStockInfo stockInfo) {
        if (option == null) return null;

        return ResProductOptionDto.builder()
                .optionId(option.getId())
                .optionName(option.getOptionName())
                .extraPrice(option.getExtraPrice())
                .stockQuantity(stockInfo != null ? stockInfo.getStockQuantity() : 0)
                .stockStatus(resolveStockStatus(stockInfo))
                .build();
    }

    private static String resolveStockStatus(ProductStockInfo stockInfo) {
        if (stockInfo != null) {
            return stockInfo.getStockStatus();
        }
        return "SOLDOUT";
    }
}
