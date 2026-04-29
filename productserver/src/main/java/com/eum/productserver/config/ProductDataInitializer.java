package com.eum.productserver.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eum.productserver.dto.request.item.save.ProductOptionSaveDto;
import com.eum.productserver.dto.request.item.save.ProductSaveDto;
import com.eum.productserver.dto.request.item.save.ProductSaveRequest;
import com.eum.productserver.dto.response.ResProductSaveDto;
import com.eum.productserver.entity.Category;
import com.eum.productserver.entity.Product;
import com.eum.productserver.repository.CategoryRepository;
import com.eum.productserver.repository.ProductRepository;
import com.eum.productserver.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ProductDataInitializer implements ApplicationRunner {

    private static final String GENERATED_DUMMY_SUFFIX = " 더미";
    private static final String PRODUCTS_SEED_DATA_PATH = "products_seed_data.json";

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    @Override
    public void run(ApplicationArguments args) {
        long existingProductCount = productRepository.count();
        if (existingProductCount > 0) {
            log.info("기존 상품 데이터가 존재하여 seed 동기화를 진행합니다. existingProductCount={}", existingProductCount);
        }

        removeGeneratedDummyProducts();

        int savedCount = 0;
        int backfilledCount = 0;
        List<SeedProduct> seedProducts = seedProducts();
        Map<String, Long> productPriceByOptionKey = productPriceByOptionKey(seedProducts);
        for (SeedProduct seedProduct : seedProducts) {
            validateSeedProduct(seedProduct);
            ClassPathResource imageResource = new ClassPathResource(seedProduct.imagePath());
            if (!imageResource.exists()) {
                log.warn("상품 seed 이미지를 찾을 수 없어 건너뜁니다. productName={}, path={}",
                        seedProduct.productName(), seedProduct.imagePath());
                continue;
            }

            Category category = categoryRepository.findByCategoryName(seedProduct.categoryName())
                    .orElseThrow(() -> new IllegalStateException(
                            "상품 seed에 필요한 카테고리를 찾을 수 없습니다. category=" + seedProduct.categoryName()
                    ));

            ProductSaveRequest request = new ProductSaveRequest();
            request.setCategoryId(category.getId());
            ProductSaveDto productSaveDto = toProductSaveDto(seedProduct, productPriceByOptionKey);
            request.setProductSaveDto(productSaveDto);

            try {
                List<MultipartFile> imageFiles = mainImageFiles(seedProduct);
                List<MultipartFile> detailFiles = detailImageFiles(seedProduct);
                Optional<Product> existingProduct = productRepository.findByProductNameAndCategory_Id(
                        seedProduct.productName(),
                        category.getId()
                );
                if (existingProduct.isPresent()) {
                    boolean backfilled = productService.addMissingSeedOptionsAndDetailImages(
                            existingProduct.get().getProductId(),
                            productSaveDto,
                            imageFiles,
                            detailFiles
                    );
                    if (backfilled) {
                        backfilledCount++;
                    }
                    continue;
                }

                if (seedProduct.productId() != null) {
                    alignProductIdSequence(seedProduct.productId());
                }
                ResProductSaveDto saved = productService.save(request, imageFiles, detailFiles);
                savedCount++;
                log.debug("상품 seed 등록: productName={}, productId={}", saved.getProductName(), saved.getProductId());
            } catch (IOException ex) {
                throw new IllegalStateException("상품 seed 이미지 파일을 읽을 수 없습니다. productName=" + seedProduct.productName(), ex);
            }
        }

        if (savedCount > 0) {
            advanceProductIdSequence();
            log.info("초기 상품 seed 등록 완료. savedCount={}", savedCount);
        }
        if (backfilledCount > 0) {
            log.info("기존 상품 seed 보강 완료. backfilledCount={}", backfilledCount);
        }
    }

    private void removeGeneratedDummyProducts() {
        List<Product> dummyProducts = productRepository.findAll().stream()
                .filter(product -> isGeneratedDummyProduct(product.getProductName()))
                .toList();

        dummyProducts.forEach(product -> productService.deleteProduct(product.getProductId(), null));

        if (!dummyProducts.isEmpty()) {
            log.info("자동 생성 더미 상품 정리 완료. removedCount={}", dummyProducts.size());
        }
    }

    private boolean isGeneratedDummyProduct(String productName) {
        return productName != null && productName.endsWith(GENERATED_DUMMY_SUFFIX);
    }

    private List<MultipartFile> mainImageFiles(SeedProduct seedProduct) throws IOException {
        String imagePath = seedProduct.imagePath();
        String parentDirectory = imagePath.substring(0, imagePath.lastIndexOf('/'));
        String normalizedKey = normalizeMainImageKey(filenameWithoutExtension(imagePath));

        List<Resource> resources = Arrays.stream(resourceResolver.getResources("classpath:" + parentDirectory + "/*"))
                .filter(Resource::isReadable)
                .filter(resource -> isImageFile(resource.getFilename()))
                .filter(resource -> matchesMainImageGroup(normalizedKey, resource.getFilename()))
                .sorted(Comparator
                        .comparingInt(this::resourceOrder)
                        .thenComparing(resource -> resource.getFilename() != null ? resource.getFilename() : ""))
                .toList();

        if (resources.isEmpty()) {
            ClassPathResource imageResource = new ClassPathResource(imagePath);
            if (!imageResource.exists()) {
                return List.of();
            }
            resources = List.of(imageResource);
        }

        return resources.stream()
                .<MultipartFile>map(resource -> {
                    try {
                        String filename = resource.getFilename();
                        return ClassPathMultipartFile.from(
                                "files",
                                filename,
                                contentType(filename),
                                resource
                        );
                    } catch (IOException ex) {
                        throw new IllegalStateException("상품 seed 이미지 파일을 읽을 수 없습니다. path=" + imagePath, ex);
                    }
                })
                .toList();
    }

    private List<MultipartFile> detailImageFiles(SeedProduct seedProduct) throws IOException {
        if (seedProduct.detailImageDirectory() == null || seedProduct.detailImageDirectory().isBlank()) {
            return List.of();
        }

        Resource[] resources;
        try {
            resources = resourceResolver.getResources("classpath:" + seedProduct.detailImageDirectory() + "/*");
        } catch (FileNotFoundException ex) {
            return List.of();
        }

        return Arrays.stream(resources)
                .filter(Resource::isReadable)
                .filter(resource -> isImageFile(resource.getFilename()))
                .sorted(Comparator
                        .comparingInt(this::resourceOrder)
                        .thenComparing(resource -> resource.getFilename() != null ? resource.getFilename() : ""))
                .<MultipartFile>map(resource -> {
                    try {
                        String filename = resource.getFilename();
                        return ClassPathMultipartFile.from(
                                "detailImageFiles",
                                filename,
                                contentType(filename),
                                resource
                        );
                    } catch (IOException ex) {
                        throw new IllegalStateException("상품 상세 seed 이미지 파일을 읽을 수 없습니다. path=" + seedProduct.detailImageDirectory(), ex);
                    }
                })
                .toList();
    }

    private int resourceOrder(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            return Integer.MAX_VALUE;
        }

        int dotIndex = filename.lastIndexOf('.');
        String nameWithoutExtension = dotIndex >= 0 ? filename.substring(0, dotIndex) : filename;
        try {
            return Integer.parseInt(nameWithoutExtension);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean matchesMainImageGroup(String normalizedKey, String filename) {
        return filename != null
                && isImageFile(filename)
                && normalizeMainImageKey(filenameWithoutExtension(filename)).equals(normalizedKey);
    }

    private boolean isImageFile(String filename) {
        if (filename == null) {
            return false;
        }

        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".jpg")
                || lowerFilename.endsWith(".jpeg")
                || lowerFilename.endsWith(".png")
                || lowerFilename.endsWith(".gif")
                || lowerFilename.endsWith(".webp");
    }

    private static String contentType(String resourcePath) {
        String lowerPath = resourcePath.toLowerCase();
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private List<SeedProduct> seedProducts() {
        ClassPathResource resource = new ClassPathResource(PRODUCTS_SEED_DATA_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            List<SeedProduct> seedProducts = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
            return seedProducts.stream()
                    .map(this::normalizeSeedProduct)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("상품 seed JSON 파일을 읽을 수 없습니다. path=" + PRODUCTS_SEED_DATA_PATH, ex);
        }
    }

    private SeedProduct normalizeSeedProduct(SeedProduct seedProduct) {
        String content = seedProduct.content();
        if ((content == null || content.isBlank()) && seedProduct.productName() != null) {
            content = defaultSeedContent(seedProduct.productName());
        }

        String detailImageDirectory = seedProduct.detailImageDirectory();
        if ((detailImageDirectory == null || detailImageDirectory.isBlank())
                && seedProduct.imagePath() != null
                && !seedProduct.imagePath().isBlank()) {
            detailImageDirectory = defaultDetailImageDirectory(seedProduct.imagePath());
        }

        List<String> optionNames = seedProduct.optionNames() != null
                ? List.copyOf(seedProduct.optionNames())
                : List.of();
        List<SeedOption> options = seedProduct.options() != null
                ? List.copyOf(seedProduct.options())
                : List.of();

        return new SeedProduct(
                seedProduct.categoryName(),
                seedProduct.productName(),
                content,
                seedProduct.slug(),
                seedProduct.imagePath(),
                detailImageDirectory,
                seedProduct.brandName(),
                seedProduct.tags(),
                seedProduct.price(),
                optionNames,
                options,
                seedProduct.productId()
        );
    }

    private void validateSeedProduct(SeedProduct seedProduct) {
        if (seedProduct.categoryName() == null || seedProduct.categoryName().isBlank()) {
            throw new IllegalStateException("상품 seed 카테고리명이 비어 있습니다. productName=" + seedProduct.productName()
                    + ", imagePath=" + seedProduct.imagePath());
        }
        if (seedProduct.productName() == null || seedProduct.productName().isBlank()) {
            throw new IllegalStateException("상품 seed 상품명이 비어 있습니다. category=" + seedProduct.categoryName()
                    + ", imagePath=" + seedProduct.imagePath());
        }
        if (seedProduct.imagePath() == null || seedProduct.imagePath().isBlank()) {
            throw new IllegalStateException("상품 seed 이미지 경로가 비어 있습니다. category=" + seedProduct.categoryName()
                    + ", productName=" + seedProduct.productName());
        }
    }

    private String defaultSeedContent(String productName) {
        return productName + " 상품입니다.";
    }

    private String normalizeMainImageKey(String value) {
        return value.replaceAll("_\\d+$", "")
                .replace(" ", "")
                .toLowerCase();
    }

    private String filenameWithoutExtension(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String defaultDetailImageDirectory(String imagePath) {
        int dotIndex = imagePath.lastIndexOf('.');
        return dotIndex >= 0 ? imagePath.substring(0, dotIndex) : imagePath;
    }

    private ProductSaveDto toProductSaveDto(SeedProduct seedProduct, Map<String, Long> productPriceByOptionKey) {
        return ProductSaveDto.builder()
                .productName(seedProduct.productName())
                .content(seedProduct.content())
                .brandName(seedProduct.brandName())
                .brandId(1L)
                .tags(seedProduct.tags())
                .price(seedProduct.price())
                .status("판매중")
                .keywords(seedProduct.tags())
                .deliveryFee(3000L)
                .deliveryMethod("택배")
                .options(seedOptions(seedProduct, productPriceByOptionKey))
                .build();
    }

    private List<ProductOptionSaveDto> seedOptions(SeedProduct seedProduct, Map<String, Long> productPriceByOptionKey) {
        List<SeedOption> explicitOptions = seedProduct.options() != null ? seedProduct.options() : List.of();
        if (!explicitOptions.isEmpty()) {
            return explicitOptions.stream()
                    .map(seedOption -> option(
                            seedOption.optionName(),
                            seedOption.extraPrice() != null ? seedOption.extraPrice() : 0L
                    ))
                    .toList();
        }

        List<String> optionNames = seedProduct.optionNames() != null ? seedProduct.optionNames() : List.of();
        return optionNames.stream()
                .map(optionName -> option(
                        optionName,
                        resolveExtraPrice(seedProduct, optionName, productPriceByOptionKey)
                ))
                .toList();
    }

    private Map<String, Long> productPriceByOptionKey(List<SeedProduct> seedProducts) {
        Map<String, Long> pricesByOptionKey = new HashMap<>();
        seedProducts.forEach(seed -> addOptionPrice(pricesByOptionKey, seed.productName(), seed.price()));
        productRepository.findAll().forEach(product ->
                addOptionPrice(pricesByOptionKey, product.getProductName(), product.getPrice())
        );
        return pricesByOptionKey;
    }

    private void addOptionPrice(Map<String, Long> pricesByOptionKey, String productName, Long price) {
        String optionKey = optionPriceKey(productName);
        if (optionKey.isBlank() || price == null) {
            return;
        }
        pricesByOptionKey.put(optionKey, price);
    }

    private Long resolveExtraPrice(SeedProduct seedProduct, String optionName, Map<String, Long> productPriceByOptionKey) {
        if (seedProduct.price() == null || optionName == null) {
            return 0L;
        }

        Long singleProductPrice = productPriceByOptionKey.get(optionPriceKey(optionName));
        if (singleProductPrice == null) {
            return 0L;
        }
        return singleProductPrice - seedProduct.price();
    }

    private String optionPriceKey(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", "")
                .replace("치킨시금치", "시금치")
                .replace("황태채", "황태")
                .replace("오독오독", "")
                .replace("바삭", "")
                .replace("어글어글", "")
                .replace("스위피", "")
                .replace("강원도", "")
                .replace("대관령", "")
                .replace("무염", "")
                .replaceAll("\\d+(kg|g|개입)?", "")
                .replaceAll("[()]", "")
                .trim();
    }

    private ProductOptionSaveDto option(@Nullable String optionName, Long extraPrice) {
        ProductOptionSaveDto option = new ProductOptionSaveDto();
        option.setOptionName(optionName);
        option.setExtraPrice(extraPrice);
        return option;
    }

    // product_id IDENTITY 시퀀스를 targetId로 맞춰 다음 INSERT가 정확히 해당 값을 받도록 한다.
    private void alignProductIdSequence(Long targetId) {
        jdbcTemplate.queryForObject(
                "SELECT setval(pg_get_serial_sequence('products', 'product_id'), ?, false)",
                Long.class,
                targetId
        );
    }

    // 모든 seed INSERT 후 시퀀스를 현재 최대 product_id로 동기화한다.
    private void advanceProductIdSequence() {
        jdbcTemplate.queryForObject(
                "SELECT setval(pg_get_serial_sequence('products', 'product_id'),"
                        + " (SELECT COALESCE(MAX(product_id), 1) FROM products), true)",
                Long.class
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedProduct(
            String categoryName,
            String productName,
            String content,
            String slug,
            String imagePath,
            String detailImageDirectory,
            String brandName,
            String tags,
            Long price,
            List<String> optionNames,
            List<SeedOption> options,
            Long productId
    ) {

        String originalFilename() {
            int slashIndex = imagePath.lastIndexOf('/');
            return slashIndex >= 0 ? imagePath.substring(slashIndex + 1) : imagePath;
        }

        String contentType() {
            return ProductDataInitializer.contentType(imagePath);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedOption(
            String optionName,
            Long extraPrice
    ) {
    }

    private static class ClassPathMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private ClassPathMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        private static ClassPathMultipartFile from(
                String name,
                String originalFilename,
                String contentType,
                Resource resource
        ) throws IOException {
            return new ClassPathMultipartFile(
                    name,
                    originalFilename,
                    contentType,
                    FileCopyUtils.copyToByteArray(resource.getInputStream())
            );
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            FileCopyUtils.copy(content, dest);
        }
    }
}
