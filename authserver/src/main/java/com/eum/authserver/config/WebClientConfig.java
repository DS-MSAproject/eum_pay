package com.eum.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // ❌ @LoadBalanced 제거됨
    // S3/MediaServer 업로드 기능 제거로 인해 로드밸런싱 불필요
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}