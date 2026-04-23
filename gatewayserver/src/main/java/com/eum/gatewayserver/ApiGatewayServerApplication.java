package com.eum.gatewayserver;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class ApiGatewayServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayServerApplication.class, args);
    }

    @PostConstruct
    public void init() {
        // 💡 모든 비동기 구간에서 MDC의 "correlationId"를 자동으로 복사하도록 설정
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(
                        "correlationId",
                        () -> MDC.get("correlationId"),
                        cid -> { if (cid != null) MDC.put("correlationId", cid); },
                        () -> MDC.remove("correlationId")
                );

        // 💡 Reactor가 스레드를 바꿀 때마다 위 설정을 실행하도록 강제
        Hooks.enableAutomaticContextPropagation();
    }
}
