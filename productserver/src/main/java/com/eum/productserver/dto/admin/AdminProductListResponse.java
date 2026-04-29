package com.eum.productserver.dto.admin;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductLifecycleStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminProductListResponse {

    private Long productId;
    private String productName;
    private String brandName;
    private Long price;
    private String status;
    private ProductLifecycleStatus lifecycleStatus;
    private String categoryName;
    private int optionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminProductListResponse from(Product p) {
        return AdminProductListResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .brandName(p.getBrandName())
                .price(p.getPrice())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .lifecycleStatus(p.getLifecycleStatus())
                .categoryName(p.getCategory() != null ? p.getCategory().getCategoryName() : null)
                .optionCount(p.getOptions() != null ? p.getOptions().size() : 0)
                .createdAt(p.getCreatedDate())
                .updatedAt(p.getModifiedDate())
                .build();
    }
}
