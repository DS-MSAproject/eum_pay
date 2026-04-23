package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductDetailImage;
import com.eum.productserver.entity.ProductImage;

import java.util.List;

/**
 * 상품 이벤트와 bootstrap 응답에서 공통으로 쓰는 상품 스냅샷 조립 유틸리티입니다.
 *
 * 대표 이미지와 옵션 스냅샷 조립 규칙을 한 곳에 모읍니다.
 */
public final class ProductSnapshotAssembler {

    private ProductSnapshotAssembler() {
    }

    public static String resolveMainImageUrl(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return product.getImageUrl();
        }

        return product.getImages().stream()
                .filter(ProductImage::isMain)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(product.getImageUrl());
    }

    public static List<String> resolveDetailImageUrls(Product product) {
        if (product.getDetailImages() == null || product.getDetailImages().isEmpty()) {
            return List.of();
        }

        return product.getDetailImages().stream()
                .sorted((left, right) -> Integer.compare(
                        left.getDisplayOrder() != null ? left.getDisplayOrder() : 0,
                        right.getDisplayOrder() != null ? right.getDisplayOrder() : 0
                ))
                .map(ProductDetailImage::getImageUrl)
                .toList();
    }

    public static List<ProductOptionSnapshotDto> resolveOptions(Product product, boolean active) {
        if (product.getOptions() == null || product.getOptions().isEmpty()) {
            return List.of();
        }

        return product.getOptions().stream()
                .map(option -> ProductOptionSnapshotDto.from(option, active))
                .toList();
    }
}
