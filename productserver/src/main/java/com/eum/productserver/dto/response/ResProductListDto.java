package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductImage;
import lombok.*;

import java.util.List;
import java.util.function.Function;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResProductListDto {

    private Long productId;
    private String productName;
    private String productUrl;
    private Long price;
    private String priceDisplay;
    private String mainImageUrl;
    private List<String> tags;
    private Integer stockQuantity;
    private String stockStatus;

    public static ResProductListDto fromEntity(Product product) {
        return fromEntity(product, null);
    }

    public static ResProductListDto fromEntity(Product product, ProductStockInfo stockInfo) {
        return fromEntity(product, stockInfo, Function.identity());
    }

    public static ResProductListDto fromEntity(
            Product product,
            ProductStockInfo stockInfo,
            Function<String, String> imageUrlMapper
    ) {
        // 메인 이미지 찾기 (없으면 null)
        String mainImg = product.getImages() != null
                ? product.getImages().stream()
                .filter(ProductImage::isMain)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElseGet(() -> resolveFallbackImageUrl(product))
                : product.getImageUrl();

        return ResProductListDto.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productUrl(product.getProductUrl())
                .price(product.getPrice())
                .priceDisplay(formatPrice(product.getPrice()))
                .tags(ProductTagResolver.resolve(product))
                .mainImageUrl(mainImg != null ? imageUrlMapper.apply(mainImg) : null)
                .stockQuantity(stockInfo != null ? stockInfo.getStockQuantity() : 0)
                .stockStatus(stockInfo != null ? stockInfo.getStockStatus() : "SOLDOUT")
                .build();
    }

    private static String resolveFallbackImageUrl(Product product) {
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            return product.getImages().get(0).getImageUrl();
        }
        return product.getImageUrl();
    }

    private static String formatPrice(Long price) {
        return price != null ? String.format("%,d원", price) : "0원";
    }
}
