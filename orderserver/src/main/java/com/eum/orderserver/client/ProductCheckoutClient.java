package com.eum.orderserver.client;

import com.eum.orderserver.dto.product.CheckoutValidationRequest;
import com.eum.orderserver.dto.product.CheckoutValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dseum-product")
public interface ProductCheckoutClient {

    @PostMapping("/product/checkout/validate")
    CheckoutValidationResponse validate(@RequestBody CheckoutValidationRequest request);
}
