package com.eum.productserver.service;

import com.eum.productserver.dto.admin.AdminProductCreateRequest;
import com.eum.productserver.dto.admin.AdminProductDetailResponse;
import com.eum.productserver.dto.admin.AdminProductListResponse;
import com.eum.productserver.dto.admin.AdminProductStatusRequest;
import com.eum.productserver.entity.Category;
import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.ProductLifecycleStatus;
import com.eum.productserver.entity.ProductOption;
import com.eum.productserver.message.ProductCreatedEvent;
import com.eum.productserver.repository.CategoryRepository;
import com.eum.productserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StreamBridge streamBridge;

    /**
     * 관리자 상품 목록 조회 (lifecycleStatus 필터링 지원)
     */
    public Page<AdminProductListResponse> listProducts(int page, int size, ProductLifecycleStatus lifecycleStatus) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "productId"));

        Page<Product> productPage;
        if (lifecycleStatus != null) {
            productPage = productRepository.findByLifecycleStatus(lifecycleStatus, pageable);
        } else {
            productPage = productRepository.findAll(pageable);
        }
        return productPage.map(AdminProductListResponse::from);
    }

    /**
     * 관리자 상품 상세 조회
     */
    public AdminProductDetailResponse getProductDetail(Long productId) {
        Product product = findProductById(productId);
        return AdminProductDetailResponse.from(product);
    }

    /**
     * 관리자 상품 생성
     */
    @Transactional
    public AdminProductDetailResponse createProduct(AdminProductCreateRequest req) {
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다. id=" + req.getCategoryId()));

        validateDogFoodDomain(category, req.getAllergens());

        Product product = Product.builder()
                .productName(req.getProductName())
                .content(req.getContent())
                .price(req.getPrice())
                .brandName(req.getBrandName())
                .imageUrl(req.getImageUrl())
                .tags(req.getTags())
                .keywords(req.getKeywords())
                .deliveryFee(req.getDeliveryFee() != null ? req.getDeliveryFee() : 0L)
                .deliveryMethod(req.getDeliveryMethod() != null ? req.getDeliveryMethod() : "일반택배")
                .allergens(req.getAllergens())
                .ingredients(req.getIngredients())
                .category(category)
                .lifecycleStatus(ProductLifecycleStatus.DRAFT)
                .build();

        // 옵션 처리
        if (req.getOptions() != null && !req.getOptions().isEmpty()) {
            for (AdminProductCreateRequest.OptionDto optDto : req.getOptions()) {
                ProductOption option = ProductOption.builder()
                        .optionName(optDto.getOptionName())
                        .extraPrice(optDto.getExtraPrice() != null ? optDto.getExtraPrice() : 0L)
                        .build();
                product.addOption(option);
            }
        }

        Product saved = productRepository.save(product);

        // 재고 초기화 이벤트 발행
        publishProductCreatedEvent(saved, req.getInitialStock());

        return AdminProductDetailResponse.from(saved);
    }

    /**
     * 관리자 상품 수정
     */
    @Transactional
    public AdminProductDetailResponse updateProduct(Long productId, AdminProductCreateRequest req) {
        Product product = findProductById(productId);

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다. id=" + req.getCategoryId()));

        product.setProductName(req.getProductName());
        product.setContent(req.getContent());
        product.setPrice(req.getPrice());
        product.setBrandName(req.getBrandName());
        product.setImageUrl(req.getImageUrl());
        product.setTags(req.getTags());
        product.setKeywords(req.getKeywords());
        product.setDeliveryFee(req.getDeliveryFee() != null ? req.getDeliveryFee() : 0L);
        product.setDeliveryMethod(req.getDeliveryMethod() != null ? req.getDeliveryMethod() : "일반택배");
        product.setAllergens(req.getAllergens());
        product.setIngredients(req.getIngredients());
        product.setCategory(category);

        // 옵션 교체: 기존 옵션 제거 후 새로 추가
        product.getOptions().clear();
        if (req.getOptions() != null && !req.getOptions().isEmpty()) {
            for (AdminProductCreateRequest.OptionDto optDto : req.getOptions()) {
                ProductOption option = ProductOption.builder()
                        .optionName(optDto.getOptionName())
                        .extraPrice(optDto.getExtraPrice() != null ? optDto.getExtraPrice() : 0L)
                        .build();
                product.addOption(option);
            }
        }

        return AdminProductDetailResponse.from(product);
    }

    /**
     * 관리자 상품 삭제
     */
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = findProductById(productId);
        productRepository.delete(product);
        log.info("[Admin] 상품 삭제 완료: productId={}", productId);
    }

    /**
     * 상품 라이프사이클 상태 전이
     */
    @Transactional
    public AdminProductDetailResponse transitionStatus(Long productId, AdminProductStatusRequest req) {
        Product product = findProductById(productId);
        product.transitionTo(req.getTargetStatus());
        log.info("[Admin] 상품 상태 전이: productId={}, targetStatus={}, reason={}",
                productId, req.getTargetStatus(), req.getReason());
        return AdminProductDetailResponse.from(product);
    }

    // ----------------------------------------
    // Private helpers
    // ----------------------------------------

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + productId));
    }

    private void publishProductCreatedEvent(Product product, int initialStock) {
        List<ProductCreatedEvent.OptionInfo> optionInfos;
        if (product.getOptions() == null || product.getOptions().isEmpty()) {
            optionInfos = List.of(ProductCreatedEvent.OptionInfo.builder()
                    .optionId(0L)
                    .optionName(null)
                    .build());
        } else {
            optionInfos = product.getOptions().stream()
                    .map(o -> ProductCreatedEvent.OptionInfo.builder()
                            .optionId(o.getId())
                            .optionName(o.getOptionName())
                            .build())
                    .toList();
        }

        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .productId(product.getProductId())
                .options(optionInfos)
                .initialStock(initialStock)
                .build();

        streamBridge.send("product-created-out-0", event);
        log.info("[Admin] PRODUCT_CREATED 이벤트 발행: productId={}", product.getProductId());
    }

    /**
     * 식품 카테고리 도메인 알러지 검증 (어드바이저리)
     */
    private void validateDogFoodDomain(Category category, String allergens) {
        if (category == null) return;
        String name = category.getCategoryName();
        if (name != null) {
            boolean isFoodCategory = name.contains("Food") || name.contains("Treat")
                    || name.contains("Bakery") || name.contains("사료")
                    || name.contains("간식") || name.contains("영양제");
            if (isFoodCategory && (allergens == null || allergens.isBlank())) {
                log.warn("[Admin] 식품 카테고리({})에 알러지 정보가 없습니다. 상품명 등록 후 보완을 권장합니다.", name);
            }
        }
    }
}
