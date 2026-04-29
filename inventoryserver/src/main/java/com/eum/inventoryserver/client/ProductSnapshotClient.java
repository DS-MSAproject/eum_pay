package com.eum.inventoryserver.client;

import com.eum.inventoryserver.client.dto.ProductSnapshotPage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "dseum-product", contextId = "productSnapshotClient")
public interface ProductSnapshotClient {

    @GetMapping("/internal/products/snapshots")
    ProductSnapshotPage getSnapshots(
            @RequestParam("lastProductId") Long lastProductId,
            @RequestParam("size") int size
    );
}
