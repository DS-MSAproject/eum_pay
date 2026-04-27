package com.eum.searchserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class SearchserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchserverApplication.class, args);
    }

}
