package com.eum.orderserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.eum.orderserver.client")
@EntityScan(basePackages = {
        "com.eum.orderserver.domain",
        "com.eum.orderserver.outbox",
        "com.eum.orderserver.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.eum.orderserver.repository",
        "com.eum.orderserver.outbox",
        "com.eum.orderserver.idempotency"
})
public class OrderServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServerApplication.class, args);
    }
}
