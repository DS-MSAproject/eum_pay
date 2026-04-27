package com.eum.inventoryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient // Eureka 등록용
@EnableScheduling
public class InventoryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServerApplication.class, args);
    }

}
