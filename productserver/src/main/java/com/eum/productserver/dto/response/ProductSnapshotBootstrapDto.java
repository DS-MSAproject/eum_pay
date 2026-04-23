package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 검색/외부 조회 모델을 처음 만들 때 productserver가 제공하는 상품 스냅샷 DTO입니다.
 *
 * 상품 기본 정보와 현재 판매 가능 여부, 옵션 스냅샷을 한 번에 내려주는 bootstrap 단위입니다.
 */
@Getter
@Builder
public class ProductSnapshotBootstrapDto {

    private Long productId;
    private String productName;
    private Long price;
    private String imageUrl;
    private List<String> detailImageUrls;
    private boolean active;
    private List<ProductOptionSnapshotDto> options;

    public static ProductSnapshotBootstrapDto from(Product product) {
        boolean active = product.isActiveForProjection();

        return ProductSnapshotBootstrapDto.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .price(product.getPrice())
                .imageUrl(ProductSnapshotAssembler.resolveMainImageUrl(product))
                .detailImageUrls(ProductSnapshotAssembler.resolveDetailImageUrls(product))
                .active(active)
                .options(ProductSnapshotAssembler.resolveOptions(product, active))
                .build();
    }
}
