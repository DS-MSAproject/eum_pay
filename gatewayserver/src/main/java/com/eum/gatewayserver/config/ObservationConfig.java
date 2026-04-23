package com.eum.gatewayserver.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration
public class ObservationConfig {
    @PostConstruct
    void init() {
        // Reactor 신호가 쓰레드를 갈아탈 때 MDC 값을 자동으로 복사해줌.
        Hooks.enableAutomaticContextPropagation();
    }
}