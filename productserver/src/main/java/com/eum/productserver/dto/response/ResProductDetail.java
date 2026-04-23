package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductDetailImage;
import com.eum.productserver.entity.ProductImage;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResProductDetail {

    private Long productId;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private String brandName;
    private Long brandId;
    private String content;
    private List<String> detailImageUrls;

    private Long price;
    private String priceDisplay;

    private String status;
    private List<String> tags;
    private String keywords;
    private Long salesCount;

    private Long deliveryFee;      // 배송비
    private String deliveryMethod; // 배송 방법
    private Integer stockQuantity;
    private String stockStatus;

    private List<String> imageUrls;
    private List<ResProductOptionDto> options;


    public static ResProductDetail fromEntity(Product product) {
        return fromEntity(product, null, Map.of());
    }

    public static ResProductDetail fromEntity(
            Product product,
            ProductStockInfo stockInfo,
            Map<Long, ProductStockInfo> optionStocks
    ) {
        return fromEntity(product, stockInfo, optionStocks, Function.identity(), List.of());
    }

    public static ResProductDetail fromEntity(
            Product product,
            ProductStockInfo stockInfo,
            Map<Long, ProductStockInfo> optionStocks,
            Function<String, String> imageUrlMapper
    ) {
        return fromEntity(product, stockInfo, optionStocks, imageUrlMapper, null);
    }

    public static ResProductDetail fromEntity(
            Product product,
            ProductStockInfo stockInfo,
            Map<Long, ProductStockInfo> optionStocks,
            Function<String, String> imageUrlMapper,
            List<ResProductOptionDto> options
    ) {
        if (product == null) return null;

        String displayStatus = product.getStatus() != null ? product.getStatus().name() : "판매중";

        return ResProductDetail.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory() != null ? product.getCategory().getCategoryName() : "미지정")
                .brandName(product.getBrandName())
                .brandId(product.getBrandId())
                .content(product.getContent())
                .detailImageUrls(resolveDetailImageUrls(product, imageUrlMapper))
                .price(product.getPrice())
                .priceDisplay(String.format("%,d원", product.getPrice()))
                .status(displayStatus)
                .tags(ProductTagResolver.resolve(product))
                .keywords(product.getKeywords())
                .salesCount(product.getSalesCount())
                .imageUrls(resolveImageUrls(product, imageUrlMapper))
                .options(options != null ? options : resolveEntityOptions(product, optionStocks))
                .deliveryFee(product.getDeliveryFee())
                .deliveryMethod(product.getDeliveryMethod())
                .stockQuantity(stockInfo != null ? stockInfo.getStockQuantity() : 0)
                .stockStatus(stockInfo != null ? stockInfo.getStockStatus() : "SOLDOUT")
                .build();

    }
    private static List<ResProductOptionDto> resolveEntityOptions(
            Product product,
            Map<Long, ProductStockInfo> optionStocks
    ) {
        return product.getOptions() != null
                ? product.getOptions().stream()
                .map(option -> ResProductOptionDto.fromEntity(option, optionStocks.get(option.getId())))
                .toList()
                : null;
    }

    private static List<String> resolveDetailImageUrls(Product product, Function<String, String> imageUrlMapper) {
        return product.getDetailImages() != null
                ? product.getDetailImages().stream()
                .sorted((left, right) -> Integer.compare(
                        left.getDisplayOrder() != null ? left.getDisplayOrder() : 0,
                        right.getDisplayOrder() != null ? right.getDisplayOrder() : 0
                ))
                .map(ProductDetailImage::getImageUrl)
                .map(imageUrlMapper)
                .toList()
                : List.of();
    }

    private static List<String> resolveImageUrls(Product product, Function<String, String> imageUrlMapper) {
        List<String> imageUrls = product.getImages() != null
                ? product.getImages().stream()
                .map(ProductImage::getImageUrl)
                .map(imageUrlMapper)
                .toList()
                : List.of();

        if (!imageUrls.isEmpty() || product.getImageUrl() == null || product.getImageUrl().isBlank()) {
            return imageUrls;
        }

        return List.of(imageUrlMapper.apply(product.getImageUrl()));
    }
}
