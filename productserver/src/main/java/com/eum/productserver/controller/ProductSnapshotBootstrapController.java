package com.eum.productserver.controller;

import com.eum.productserver.dto.response.ProductSnapshotBootstrapPageDto;
import com.eum.productserver.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class ProductSnapshotBootstrapController {

    private final ProductService productService;

    @GetMapping("/snapshots")
    public ResponseEntity<ProductSnapshotBootstrapPageDto> getSnapshots(
            @RequestParam(defaultValue = "0") Long lastProductId,
            @RequestParam(defaultValue = "500") int size
    ) {
        return ResponseEntity.ok(productService.getBootstrapSnapshots(lastProductId, size));
    }
}
