package com.eum.productserver.controller;

import com.eum.productserver.dto.response.ResProductOptionDto;
import com.eum.productserver.service.ProductOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductOptionController {

    private final ProductOptionService productOptionService;

    /**
     * 1. 특정 상품의 모든 옵션 목록 조회
     */
    @GetMapping("/{productId}/options")
    public ResponseEntity<List<ResProductOptionDto>> getOptionsByProduct(@PathVariable Long productId) {
        List<ResProductOptionDto> responses = productOptionService.findOptionsByProductId(productId);
        return ResponseEntity.ok(responses);
    }

}
