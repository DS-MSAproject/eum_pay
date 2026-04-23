package com.eum.productserver;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing // 이게 있어야 시간이 자동으로 들어감
@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.eum")
@RefreshScope
public class ProductServerApplication {

    public static void main(String[] args)
    {
        SpringApplication.run(ProductServerApplication.class, args);
    }

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager em) {
        return new JPAQueryFactory(em);
    }
}
