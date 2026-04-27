package com.eum.cartserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.eum.cartserver.client")
public class CartServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServerApplication.class, args);
    }
}
