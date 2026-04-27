package com.eum.gatewayserver.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    // IP 기반 Rate Limiting KeyResolver
    // X-Forwarded-For 헤더 우선 사용 (프록시/로드밸런서 뒤의 실제 IP)
    // 없으면 RemoteAddress 사용
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getHeaders()
                    .getFirst("X-Forwarded-For");

            if (ip == null || ip.isBlank()) {
                ip = exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
            } else {
                // X-Forwarded-For는 "client, proxy1, proxy2" 형식 — 첫 번째가 실제 클라이언트
                ip = ip.split(",")[0].trim();
            }

            return Mono.just(ip);
        };
    }
}
