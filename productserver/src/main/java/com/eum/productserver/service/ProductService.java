package com.eum.productserver.service;

import com.eum.productserver.client.InventoryStockClient;
import com.eum.productserver.dto.inventory.InventoryStockRequest;
import com.eum.productserver.dto.inventory.InventoryStockResponse;
import com.eum.productserver.dto.request.item.save.ImageFileSaveDto;
import com.eum.productserver.dto.request.item.save.ProductOptionSaveDto;
import com.eum.productserver.dto.request.item.save.ProductSaveDto;
import com.eum.productserver.dto.request.item.save.ProductSaveRequest;
import com.eum.productserver.dto.request.item.update.ProductUpdateRequest;
import com.eum.productserver.dto.response.ProductFrontendDto;
import com.eum.productserver.dto.response.ProductFrontendOptionDto;
import com.eum.productserver.dto.response.ProductStockInfo;
import com.eum.productserver.dto.response.ProductSnapshotBootstrapDto;
import com.eum.productserver.dto.response.ProductSnapshotBootstrapPageDto;
import com.eum.productserver.dto.response.ResProductDetail;
import com.eum.productserver.dto.response.ResProductListDto;
import com.eum.productserver.dto.response.ResProductOptionDto;
import com.eum.productserver.dto.response.ResProductSaveDto;
import com.eum.productserver.dto.response.ResProductUpdateDto;
import com.eum.productserver.entity.*;
import com.eum.productserver.repository.CategoryRepository;
import com.eum.productserver.repository.ProductOptionRepository;
import com.eum.productserver.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * productserver의 상품 생성, 수정, 삭제, 조회를 담당하는 핵심 서비스입니다.
 *
 * 상품 자체 데이터는 이 서버가 저장하지만 재고 원장은 inventoryserver가 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductService {

    private static final long NO_OPTION_ID = 0L;
    private static final String PRODUCT_URL_PREFIX = "https://www.eum.com/product/";
    private static final int MAX_PRODUCT_IMAGE_COUNT = 20;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductOptionRepository productOptionRepository;
    private final InventoryStockClient inventoryStockClient;
    private final ProductImageUploadService productImageUploadService;
    private final ProductImageUrlService productImageUrlService;

    // 1. 상품 검색 (카테고리별 필터링 + 페이징)
    public Page<ResProductListDto> findAll(Long categoryId, Pageable pageable) {
        Page<Product> productPage = productRepository.findByCategoryId(categoryId, pageable);
        Map<Long, ProductStockInfo> stocksByProductId = toProductStocks(fetchInventoryStocks(
                productPage.getContent().stream()
                        .map(Product::getProductId)
                        .toList()
        ));

        return productPage.map(product -> ResProductListDto.fromEntity(
                product,
                stocksByProductId.get(product.getProductId()),
                productImageUrlService::toDisplayUrl
        ));
    }

    // 상품 상세 조회
    public ResProductDetail findProductDetail(Long productId) {
        log.info("ProductService.findProductDetail productId= {}", productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("해당 상품을 찾을 수 없습니다."));

        List<InventoryStockResponse> inventoryStocks = fetchInventoryStocks(List.of(productId));
        ProductStockInfo productStock = toProductStocks(inventoryStocks).get(productId);
        Map<Long, ProductStockInfo> optionStocksByOptionId = toOptionStocks(inventoryStocks);

        return ResProductDetail.fromEntity(
                product,
                productStock,
                optionStocksByOptionId,
                productImageUrlService::toDisplayUrl,
                resolveDetailOptions(product, optionStocksByOptionId)
        );
    }

    private List<InventoryStockResponse> fetchInventoryStocks(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        List<InventoryStockResponse> response = inventoryStockClient.getStocks(
                new InventoryStockRequest(productIds)
        );
        return response != null ? response : List.of();
    }

    private Map<Long, ProductStockInfo> toProductStocks(List<InventoryStockResponse> inventoryStocks) {
        Map<Long, ProductStockInfo> productStocks = new HashMap<>();
        Map<Long, Integer> optionTotalsByProductId = new HashMap<>();

        for (InventoryStockResponse stock : inventoryStocks) {
            if (stock.getProductId() == null) {
                continue;
            }

            int quantity = defaultQuantity(stock.getStockQuantity());
            if (isNoOptionId(stock.getOptionId())) {
                productStocks.put(
                        stock.getProductId(),
                        new ProductStockInfo(stock.getProductId(), null, quantity, defaultStatus(stock.getStockStatus(), quantity))
                );
                continue;
            }

            optionTotalsByProductId.merge(stock.getProductId(), quantity, Integer::sum);
        }

        optionTotalsByProductId.forEach((productId, totalQuantity) ->
                productStocks.put(productId, new ProductStockInfo(productId, null, totalQuantity, resolveStockStatus(totalQuantity)))
        );

        return productStocks;
    }

    private Map<Long, ProductStockInfo> toOptionStocks(List<InventoryStockResponse> inventoryStocks) {
        return inventoryStocks.stream()
                .filter(stock -> stock.getProductId() != null && !isNoOptionId(stock.getOptionId()))
                .collect(Collectors.toMap(
                        InventoryStockResponse::getOptionId,
                        stock -> {
                            int quantity = defaultQuantity(stock.getStockQuantity());
                            return new ProductStockInfo(
                                    stock.getProductId(),
                                    stock.getOptionId(),
                                    quantity,
                                    defaultStatus(stock.getStockStatus(), quantity)
                            );
                        },
                        (current, replacement) -> replacement
                ));
    }

    private boolean isNoOptionId(Long optionId) {
        return optionId == null || optionId == NO_OPTION_ID;
    }

    private int defaultQuantity(Integer quantity) {
        return quantity != null ? quantity : 0;
    }

    private String defaultStatus(String status, int quantity) {
        return status != null ? status : resolveStockStatus(quantity);
    }

    private String resolveStockStatus(int quantity) {
        return quantity > 0 ? "AVAILABLE" : "SOLDOUT";
    }

    public ProductFrontendDto getFrontendProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("해당 상품을 찾을 수 없습니다."));

        return ProductFrontendDto.builder()
                .imageUrl(productImageUrlService.toDisplayUrl(resolveFrontendImageUrl(product)))
                .productId(product.getProductId())
                .productName(product.getProductName())
                .price(product.getPrice())
                .options(product.getOptions().stream()
                        .map(option -> ProductFrontendOptionDto.builder()
                                .optionId(option.getId())
                                .optionName(option.getOptionName())
                                .build())
                        .toList())
                .build();
    }

    private List<ResProductOptionDto> resolveDetailOptions(
            Product product,
            Map<Long, ProductStockInfo> optionStocksByOptionId
    ) {
        return product.getOptions().stream()
                .map(option -> ResProductOptionDto.fromEntity(option, optionStocksByOptionId.get(option.getId())))
                .toList();
    }

    public ProductSnapshotBootstrapPageDto getBootstrapSnapshots(Long lastProductId, int size) {
        int pageSize = Math.min(Math.max(size, 1), 500);
        long cursor = lastProductId != null ? lastProductId : 0L;

        List<Product> products = productRepository.findByProductIdGreaterThanOrderByProductIdAsc(
                cursor,
                PageRequest.of(0, pageSize)
        );

        List<ProductSnapshotBootstrapDto> items = products.stream()
                .map(ProductSnapshotBootstrapDto::from)
                .toList();

        Long nextLastProductId = items.isEmpty() ? cursor : items.get(items.size() - 1).getProductId();

        return ProductSnapshotBootstrapPageDto.builder()
                .items(items)
                .nextLastProductId(nextLastProductId)
                .hasNext(items.size() == pageSize)
                .build();
    }

    // 2. 상품 저장
    public ResProductSaveDto save(ProductSaveRequest request) {
        return save(request, List.of(), null);
    }

    // multipart 파일이 포함된 상품 등록 요청입니다. 파일을 먼저 S3 URL로 바꾼 뒤 기존 이미지 저장 로직을 재사용합니다.
    public ResProductSaveDto save(ProductSaveRequest request, List<MultipartFile> imageFiles) {
        return save(request, imageFiles, null);
    }

    public ResProductSaveDto save(
            ProductSaveRequest request,
            List<MultipartFile> imageFiles,
            List<MultipartFile> detailImageFiles
    ) {
        List<ImageFileSaveDto> images = resolveImages(request.getImageFileSaveDtoList(), imageFiles);
        List<ImageFileSaveDto> detailImages = resolveDetailImages(
                request.getDetailImageFileSaveDtoList(),
                detailImageFiles
        );
        addLegacyImageUrlIfOnlyImage(images, request.getProductSaveDto().getImageUrl());
        validateImageLimit(images);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));

        Product product = request.getProductSaveDto().toEntity();
        product.setCategory(category);
        addImages(product, images);
        addDetailImages(product, detailImages);

        if (request.getProductSaveDto().getOptions() != null) {
            request.getProductSaveDto().getOptions()
                    .forEach(optionDto -> product.addOption(optionDto.toEntity(product)));
        }

        Product savedProduct = productRepository.saveAndFlush(product);
        savedProduct.setProductUrl(generateProductUrl(savedProduct));
        productRepository.flush();

        return ResProductSaveDto.fromEntity(savedProduct);
    }

    public boolean addMissingSeedOptionsAndDetailImages(
            Long productId,
            ProductSaveDto seedDto,
            List<MultipartFile> imageFiles,
            List<MultipartFile> detailImageFiles
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        List<ImageFileSaveDto> seedImages = resolveImages(List.of(), imageFiles);
        List<ProductOptionSaveDto> optionDtos = seedDto != null ? seedDto.getOptions() : List.of();
        boolean optionsChanged = syncSeedOptions(product, optionDtos);

        boolean imagesUpdated = false;
        if (hasImages(seedImages) && (product.getImages() == null || product.getImages().size() < seedImages.size())) {
            product.getImages().clear();
            product.setImageUrl(null);
            addImages(product, seedImages);
            imagesUpdated = true;
        }

        boolean detailImagesAdded = false;
        if (product.getDetailImages() == null || product.getDetailImages().isEmpty()) {
            List<ImageFileSaveDto> detailImages = resolveDetailImages(List.of(), detailImageFiles);
            if (hasImages(detailImages)) {
                addDetailImages(product, detailImages);
                detailImagesAdded = true;
            }
        }

        if (!optionsChanged && !imagesUpdated && !detailImagesAdded) {
            return false;
        }

        if (optionsChanged || imagesUpdated || detailImagesAdded) {
            productRepository.flush();
        }

        return true;
    }

    private boolean syncSeedOptions(Product product, List<ProductOptionSaveDto> optionDtos) {
        List<ProductOptionSaveDto> targetOptions = optionDtos != null ? optionDtos : List.of();
        List<ProductOption> currentOptions = product.getOptions() != null ? product.getOptions() : List.of();

        if (hasSameOptions(currentOptions, targetOptions)) {
            return false;
        }

        if (product.getOptions() != null) {
            product.getOptions().clear();
        }
        targetOptions.forEach(optionDto -> product.addOption(optionDto.toEntity(product)));
        return true;
    }

    private boolean hasSameOptions(List<ProductOption> currentOptions, List<ProductOptionSaveDto> targetOptions) {
        if (currentOptions.size() != targetOptions.size()) {
            return false;
        }

        for (int index = 0; index < currentOptions.size(); index++) {
            ProductOption current = currentOptions.get(index);
            ProductOptionSaveDto target = targetOptions.get(index);
            if (!Objects.equals(current.getOptionName(), target.getOptionName())
                    || !Objects.equals(current.getExtraPrice(), target.getExtraPrice())) {
                return false;
            }
        }
        return true;
    }

    private String generateProductUrl(Product product) {
        return PRODUCT_URL_PREFIX + product.getProductId();
    }


    // 3. 상품 수정
    public ResProductUpdateDto update(ProductUpdateRequest request) {
        return update(request, List.of(), null);
    }

    // multipart 파일이 포함된 상품 수정 요청입니다. 새 파일이 있으면 S3 업로드 후 기존 이미지 교체 흐름에 포함합니다.
    public ResProductUpdateDto update(ProductUpdateRequest request, List<MultipartFile> imageFiles) {
        return update(request, imageFiles, null);
    }

    public ResProductUpdateDto update(
            ProductUpdateRequest request,
            List<MultipartFile> imageFiles,
            List<MultipartFile> detailImageFiles
    ) {
        List<ImageFileSaveDto> images = resolveImages(request.getImageFileSaveDtoList(), imageFiles);
        List<ImageFileSaveDto> detailImages = resolveDetailImages(
                request.getDetailImageFileSaveDtoList(),
                detailImageFiles
        );
        validateImageLimit(images);

        Product product = productRepository.findById(request.getProductUpdateDto().getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));

        product.updateProduct(category, request.getProductUpdateDto());
        applyOptionUpdates(product, request.getProductUpdateDto().getOptions());

        if (hasImages(images)) {
            product.getImages().clear();
            addImages(product, images);
        }

        if (hasImages(detailImages)) {
            product.getDetailImages().clear();
            addDetailImages(product, detailImages);
        }

        return ResProductUpdateDto.fromEntity(product);
    }

    private List<ImageFileSaveDto> resolveDetailImages(
            List<ImageFileSaveDto> requestedDetailImages,
            List<MultipartFile> detailImageFiles
    ) {
        List<ImageFileSaveDto> detailImages = new ArrayList<>();
        if (requestedDetailImages != null) {
            detailImages.addAll(requestedDetailImages);
        }

        List<MultipartFile> actualFiles = productImageUploadService.filterActualFiles(detailImageFiles);
        if (!actualFiles.isEmpty()) {
            detailImages.addAll(productImageUploadService.uploadAll(actualFiles));
        }

        return detailImages;
    }

    private List<ImageFileSaveDto> resolveImages(List<ImageFileSaveDto> requestedImages, List<MultipartFile> imageFiles) {
        List<ImageFileSaveDto> images = new ArrayList<>();
        if (requestedImages != null) {
            images.addAll(requestedImages);
        }

        List<MultipartFile> actualFiles = productImageUploadService.filterActualFiles(imageFiles);
        if (images.size() + actualFiles.size() > MAX_PRODUCT_IMAGE_COUNT) {
            throw new IllegalArgumentException("이미지는 최대 " + MAX_PRODUCT_IMAGE_COUNT + "장까지만 등록할 수 있습니다.");
        }

        if (!actualFiles.isEmpty()) {
            images.addAll(productImageUploadService.uploadAll(actualFiles));
        }

        return images;
    }

    private void validateImageLimit(List<ImageFileSaveDto> images) {
        if (images != null && images.size() > MAX_PRODUCT_IMAGE_COUNT) {
            throw new IllegalArgumentException("이미지는 최대 " + MAX_PRODUCT_IMAGE_COUNT + "장까지만 등록할 수 있습니다.");
        }
    }

    private boolean hasImages(List<ImageFileSaveDto> images) {
        return images != null && !images.isEmpty();
    }

    private void addLegacyImageUrlIfOnlyImage(List<ImageFileSaveDto> images, String imageUrl) {
        if (hasImages(images) || imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        ImageFileSaveDto image = new ImageFileSaveDto();
        image.setImageUrl(imageUrl);
        image.setImageKey(imageUrl);
        image.setMain(true);
        images.add(image);
    }

    private void addImages(Product product, List<ImageFileSaveDto> images) {
        if (!hasImages(images)) {
            return;
        }

        for (int i = 0; i < images.size(); i++) {
            ImageFileSaveDto image = images.get(i);
            if (image.getImageUrl() == null || image.getImageUrl().isBlank()) {
                throw new IllegalArgumentException("이미지 URL은 필수입니다.");
            }

            product.addImage(ProductImage.builder()
                    .imageUrl(image.getImageUrl())
                    .imageKey(resolveImageKey(image))
                    .isMain(image.isMain() || i == 0)
                    .product(product)
                    .build());
            if (i == 0 && (product.getImageUrl() == null || product.getImageUrl().isBlank())) {
                product.setImageUrl(image.getImageUrl());
            }
        }
    }

    private void addDetailImages(Product product, List<ImageFileSaveDto> detailImages) {
        if (!hasImages(detailImages)) {
            return;
        }

        for (int i = 0; i < detailImages.size(); i++) {
            ImageFileSaveDto image = detailImages.get(i);
            if (image.getImageUrl() == null || image.getImageUrl().isBlank()) {
                throw new IllegalArgumentException("상세 이미지 URL은 필수입니다.");
            }

            product.addDetailImage(ProductDetailImage.builder()
                    .imageUrl(image.getImageUrl())
                    .imageKey(resolveImageKey(image))
                    .displayOrder(i + 1)
                    .product(product)
                    .build());
        }
    }

    private String resolveImageKey(ImageFileSaveDto image) {
        if (image.getImageKey() != null && !image.getImageKey().isBlank()) {
            return image.getImageKey();
        }
        return image.getImageUrl();
    }

    private void applyOptionUpdates(Product product, List<com.eum.productserver.dto.request.item.update.ProductOptionUpdateDto> optionUpdates) {
        if (optionUpdates == null || optionUpdates.isEmpty()) {
            return;
        }

        Map<Long, ProductOption> existingOptions = productOptionRepository.findByProduct_ProductId(product.getProductId()).stream()
                .collect(Collectors.toMap(ProductOption::getId, Function.identity()));

        Set<Long> seenOptionIds = new HashSet<>();
        for (com.eum.productserver.dto.request.item.update.ProductOptionUpdateDto optionUpdate : optionUpdates) {
            if (optionUpdate.getOptionId() == null) {
                throw new IllegalArgumentException("옵션 구조 변경은 지원하지 않습니다. 기존 optionId만 수정할 수 있습니다.");
            }

            if (!seenOptionIds.add(optionUpdate.getOptionId())) {
                throw new IllegalArgumentException("중복된 optionId가 요청에 포함되어 있습니다. optionId=" + optionUpdate.getOptionId());
            }

            ProductOption option = existingOptions.get(optionUpdate.getOptionId());
            if (option == null) {
                throw new IllegalArgumentException("해당 상품에 속하지 않은 옵션은 수정할 수 없습니다. optionId=" + optionUpdate.getOptionId());
            }

            option.updateOption(
                    optionUpdate.getOptionName() != null ? optionUpdate.getOptionName() : option.getOptionName(),
                    optionUpdate.getExtraPrice() != null ? optionUpdate.getExtraPrice() : option.getExtraPrice()
            );
        }
    }

    private String resolveFrontendImageUrl(Product product) {
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            return product.getImages().stream()
                    .filter(ProductImage::isMain)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(product.getImages().get(0).getImageUrl());
        }
        return product.getImageUrl();
    }

    // 4. 상품 삭제
    public void deleteProduct(Long id, Long sellerId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 상품을 찾을 수 없습니다."));

        productRepository.delete(product);

    }
}
