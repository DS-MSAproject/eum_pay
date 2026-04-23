package com.eum.productserver.dto.request.item.save;

import com.eum.productserver.entity.ProductImage;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageFileSaveDto {
    private String imageUrl; // S3 전체 경로
    private String imageKey; // S3 관리용 키
    private boolean isMain; // 대표 이미지 여부

    public ProductImage toEntity() {
        return ProductImage.builder()
                .imageUrl(this.imageUrl)
                .imageKey(this.imageKey)
                .isMain(this.isMain)
                .build();
    }
}
