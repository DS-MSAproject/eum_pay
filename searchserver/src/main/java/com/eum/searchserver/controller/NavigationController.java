package com.eum.searchserver.controller;

import com.eum.searchserver.dto.response.NavigationApiMeta;
import com.eum.searchserver.dto.response.NavigationMenuResponse;
import com.eum.searchserver.dto.response.NavigationTabResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search/navigation")
public class NavigationController {

    @GetMapping
    public Mono<NavigationMenuResponse> getNavigation() {
        List<NavigationTabResponse> tabs = List.of(
                new NavigationTabResponse(
                        "STORE",
                        "STORE",
                        "🐾",
                        "/product/list?categoryId=ALL",
                        new NavigationApiMeta(
                                "GET",
                                "/search/products",
                                Map.of("category", "ALL"),
                                "data"
                        )
                ),
                new NavigationTabResponse(
                        "BESTSELLER",
                        "베스트셀러",
                        "🔥",
                        "/product/bestseller",
                        new NavigationApiMeta(
                                "GET",
                                "/search/products/bestseller",
                                Map.of(),
                                "data"
                        )
                ),
                new NavigationTabResponse(
                        "BRAND",
                        "브랜드",
                        "📖",
                        "/search/brand-story/detail",
                        new NavigationApiMeta(
                                "GET",
                                "/search/brand-story/detail",
                                Map.of(),
                                "data"
                        )
                )
        );

        return Mono.just(NavigationMenuResponse.success(tabs));
    }
}
