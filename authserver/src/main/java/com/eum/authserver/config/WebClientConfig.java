package com.eum.authserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${admin.elasticsearch.url:http://elasticsearch:9200}")
    private String elasticsearchUrl;

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean("elasticsearchWebClient")
    public WebClient elasticsearchWebClient() {
        return WebClient.builder().baseUrl(elasticsearchUrl).build();
    }
}
