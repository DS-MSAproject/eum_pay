package com.eum.productserver.dto.request.item.save;

import com.eum.productserver.entity.ProductImage;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageFileUpdateDto {
    private String imageUrl;
    private String imageKey;
    private boolean isMain;

    public ProductImage toEntity() {
        return ProductImage.builder()
                .imageUrl(this.imageUrl)
                .imageKey(this.imageKey)
                .isMain(this.isMain)
                .build();
    }
}