package com.eum.productserver.dto.request.item.save;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductOption;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@NoArgsConstructor
public class ProductOptionSaveDto {

    @Nullable
    private String optionName;
    private Long extraPrice;

    /**
     * DTO -> Entity 변환
     */
    public ProductOption toEntity(Product product) {
        return ProductOption.builder()
                .optionName(this.optionName)
                .extraPrice(this.extraPrice != null ? this.extraPrice : 0)
                .product(product)
                .build();
    }
}
