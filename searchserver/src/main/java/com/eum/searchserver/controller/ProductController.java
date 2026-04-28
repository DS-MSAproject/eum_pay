package com.eum.searchserver.controller;

import com.eum.searchserver.dto.request.ProductSearchCondition;
import com.eum.searchserver.dto.response.*;
import com.eum.searchserver.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.util.StringUtils.hasText;

@RestController
@RequestMapping("/search/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * [최종] 상품 검색 및 스토어 조회
     * 개조된 SearchPageResponse의 extra 필드를 활용합니다.
     */
    @GetMapping
    public Mono<SearchPageResponse<ProductSearchResponse>> search(ProductSearchCondition condition) {
        boolean isSearchAction = hasText(condition.keyword()) || hasText(condition.title());

        if (isSearchAction) {
            return Mono.zip(
                    productService.searchAdvanced(condition),
                    productService.getTrendingKeywords().map(TrendingKeywordResponse::keyword).collectList()
            ).map(tuple -> {
                SearchPageResponse<ProductSearchResponse> response = tuple.getT1();

                // 💡 [개조 포인트] 인기 검색어를 Map에 담아 extra로 전달
                java.util.Map<String, Object> extra = java.util.Map.of("trendingKeywords", tuple.getT2());

                return SearchPageResponse.of(
                        response.data(),
                        response.totalElements(), // 필드명 변경 반영 (totalProductNumber -> totalElements)
                        response.currentPage(),
                        response.size(),
                        extra
                );
            });
        }
        return productService.searchAdvanced(condition);
    }

    /**
     * 💡 베스트셀러 탭
     */
    @GetMapping("/bestseller")
    public Mono<SearchPageResponse<BestsellerProductResponse>> getBestsellers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        return productService.getBestsellers(page, size);
    }

    /**
     * 홈 화면 베스트셀러 TOP N (임시 집계: 최신성 100%)
     */
    @GetMapping("/home-bestseller")
    public Mono<SearchPageResponse<HomeBestsellerProductResponse>> getHomeBestsellers(
            @RequestParam(defaultValue = "3") int size
    ) {
        return productService.getHomeBestsellers(size);
    }

    /**
     * 우리 아이 취향 저격 제품
     * 고정 브랜드 태그(오독오독, 어글어글, 스위피) 중 선택 브랜드 최신 3개 반환
     */
    @GetMapping("/taste-picks")
    public Mono<SearchPageResponse<TastePickProductResponse>> getTastePicks(
            @RequestParam(required = false) String brandName
    ) {
        return productService.getTastePicks(brandName);
    }

    /**
     * 메인 히어로 배너 상품 3개
     */
    @GetMapping("/main-banners")
    public Mono<SearchPageResponse<MainBannerProductResponse>> getMainBanners() {
        return productService.getMainHeroBanners();
    }

    /**
     * 상세 상품 기준 유사 상품 TOP N
     * 기본 3개 노출
     */
    @GetMapping("/{productId}/similar")
    public Mono<SearchPageResponse<SimilarProductResponse>> getSimilarProducts(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "3") int size
    ) {
        return productService.getSimilarProducts(productId, size);
    }

    /**
     * 상세 상품 기준 함께 구매하면 좋은 상품 TOP N
     * 기본 3개 노출
     */
    @GetMapping("/{productId}/together")
    public Mono<SearchPageResponse<TogetherProductResponse>> getTogetherProducts(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "3") int size
    ) {
        return productService.getTogetherProducts(productId, size);
    }

    /**
     * 실시간 자동완성 제안
     */
    @GetMapping("/autocomplete")
    public Flux<AutocompleteResponse> autocomplete(String name) {
        if (!hasText(name)) return Flux.empty();
        return productService.getAutocompleteSuggestions(name);
    }

    /**
     * 인기 검색어 단독 조회 (메인 돋보기 클릭 시)
     */
    @GetMapping("/trending")
    public Flux<TrendingKeywordResponse> getTrendingKeywords() {
        return productService.getTrendingKeywords();
    }
}
