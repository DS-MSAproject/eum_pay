package com.eum.cartserver.client;

import com.eum.cartserver.client.dto.InventoryStockRequest;
import com.eum.cartserver.client.dto.InventoryStockResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "dseum-inventory", contextId = "cartInventoryStockClient")
public interface InventoryStockClient {

    @PostMapping("/internal/inventory/stocks")
    List<InventoryStockResponse> getStocks(@RequestBody InventoryStockRequest request);
}
