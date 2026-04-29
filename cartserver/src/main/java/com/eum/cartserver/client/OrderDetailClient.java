package com.eum.cartserver.client;

import com.eum.cartserver.client.dto.OrderDetailDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "dseum-order", contextId = "cartOrderDetailClient")
public interface OrderDetailClient {

    @GetMapping("/orders/{orderId}")
    OrderDetailDto getOrderDetail(@PathVariable("orderId") Long orderId);
}
