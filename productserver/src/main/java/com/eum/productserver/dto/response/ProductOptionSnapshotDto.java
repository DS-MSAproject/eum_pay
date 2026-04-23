package com.eum.productserver.dto.response;

import com.eum.productserver.entity.ProductOption;
import lombok.Builder;
import lombok.Getter;
import org.springframework.lang.Nullable;

@Getter
@Builder
public class ProductOptionSnapshotDto {

    private Long optionId;
    @Nullable
    private String optionName;
    private Long extraPrice;
    private String stockStatus;

    public static ProductOptionSnapshotDto from(ProductOption option, boolean active) {
        return ProductOptionSnapshotDto.builder()
                .optionId(option.getId())
                .optionName(option.getOptionName())
                .extraPrice(option.getExtraPrice() != null ? option.getExtraPrice() : 0L)
                .stockStatus(active ? "AVAILABLE" : "SOLDOUT")
                .build();
    }
}
