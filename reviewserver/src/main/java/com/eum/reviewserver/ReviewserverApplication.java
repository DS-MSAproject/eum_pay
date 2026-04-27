package com.eum.reviewserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.eum")
public class ReviewserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewserverApplication.class, args);
    }

}
