package com.eum.productserver.dto.admin;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductLifecycleStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class AdminProductDetailResponse {

    private Long productId;
    private String productName;
    private String brandName;
    private Long price;
    private String status;
    private ProductLifecycleStatus lifecycleStatus;
    private Long categoryId;
    private String categoryName;
    private int optionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 상세 전용 필드
    private String content;
    private String imageUrl;
    private String tags;
    private String keywords;
    private String allergens;
    private String ingredients;
    private Long deliveryFee;
    private String deliveryMethod;
    private String productUrl;
    private List<OptionItem> options;
    private List<ImageItem> images;
    private List<DetailImageItem> detailImages;

    @Getter
    @Builder
    public static class OptionItem {
        private Long optionId;
        private String optionName;
        private Long extraPrice;
    }

    @Getter
    @Builder
    public static class ImageItem {
        private Long imageId;
        private String imageUrl;
        private String imageKey;
        private Boolean isMain;
    }

    @Getter
    @Builder
    public static class DetailImageItem {
        private Long imageId;
        private String imageUrl;
        private String imageKey;
        private Integer displayOrder;
    }

    public static AdminProductDetailResponse from(Product p) {
        List<OptionItem> optionItems = null;
        if (p.getOptions() != null) {
            optionItems = p.getOptions().stream()
                    .map(o -> OptionItem.builder()
                            .optionId(o.getId())
                            .optionName(o.getOptionName())
                            .extraPrice(o.getExtraPrice())
                            .build())
                    .collect(Collectors.toList());
        }

        List<ImageItem> imageItems = null;
        if (p.getImages() != null) {
            imageItems = p.getImages().stream()
                    .map(img -> ImageItem.builder()
                            .imageId(img.getId())
                            .imageUrl(img.getImageUrl())
                            .imageKey(img.getImageKey())
                            .isMain(img.isMain())
                            .build())
                    .collect(Collectors.toList());
        }

        List<DetailImageItem> detailImageItems = null;
        if (p.getDetailImages() != null) {
            detailImageItems = p.getDetailImages().stream()
                    .map(di -> DetailImageItem.builder()
                            .imageId(di.getId())
                            .imageUrl(di.getImageUrl())
                            .imageKey(di.getImageKey())
                            .displayOrder(di.getDisplayOrder())
                            .build())
                    .collect(Collectors.toList());
        }

        return AdminProductDetailResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .brandName(p.getBrandName())
                .price(p.getPrice())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .lifecycleStatus(p.getLifecycleStatus())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getCategoryName() : null)
                .optionCount(p.getOptions() != null ? p.getOptions().size() : 0)
                .createdAt(p.getCreatedDate())
                .updatedAt(p.getModifiedDate())
                .content(p.getContent())
                .imageUrl(p.getImageUrl())
                .tags(p.getTags())
                .keywords(p.getKeywords())
                .allergens(p.getAllergens())
                .ingredients(p.getIngredients())
                .deliveryFee(p.getDeliveryFee())
                .deliveryMethod(p.getDeliveryMethod())
                .productUrl(p.getProductUrl())
                .options(optionItems)
                .images(imageItems)
                .detailImages(detailImageItems)
                .build();
    }
}
