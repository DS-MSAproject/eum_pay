package com.eum.cartserver.client;

import com.eum.cartserver.client.dto.ProductFrontendDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "dseum-product")
public interface ProductCatalogClient {

    @GetMapping("/product/frontend/{productId}")
    ProductFrontendDto getFrontendProduct(@PathVariable("productId") Long productId);
}
