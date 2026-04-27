package com.eum.productserver.controller;

import com.eum.productserver.dto.response.ProductFrontendDto;
import com.eum.productserver.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product/frontend")
@RequiredArgsConstructor
public class ProductFrontendController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<ProductFrontendDto> getProductFrontend(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getFrontendProduct(productId));
    }
}
