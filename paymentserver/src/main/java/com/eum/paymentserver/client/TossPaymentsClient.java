package com.eum.paymentserver.client;

import com.eum.paymentserver.config.PaymentPolicyProperties;
import com.eum.paymentserver.config.TossPaymentProperties;
import com.eum.paymentserver.dto.TossCancelRequest;
import com.eum.paymentserver.dto.TossConfirmRequest;
import com.eum.paymentserver.dto.TossPaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TossPaymentsClient {

    private static final String CIRCUIT_BREAKER_NAME = "toss";

    private final TossPaymentProperties tossPaymentProperties;
    private final PaymentPolicyProperties paymentPolicyProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private WebClient webClient;
    private CircuitBreaker circuitBreaker;

    public TossPaymentsClient(
            TossPaymentProperties tossPaymentProperties,
            PaymentPolicyProperties paymentPolicyProperties,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.tossPaymentProperties = tossPaymentProperties;
        this.paymentPolicyProperties = paymentPolicyProperties;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    void init() {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        this.webClient = WebClient.builder()
                .baseUrl(tossPaymentProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(tossPaymentProperties.getSecretKey()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<TossPaymentResponse> confirm(String idempotencyKey, TossConfirmRequest request) {
        String authHeader = basicAuthHeader(tossPaymentProperties.getSecretKey());
        log.info("[TOSS-DEBUG] ===== Toss 승인 요청 =====");
        log.info("[TOSS-DEBUG] URL            : {}{}", tossPaymentProperties.getBaseUrl(), tossPaymentProperties.getConfirmPath());
        log.info("[TOSS-DEBUG] Secret-Key     : {}", tossPaymentProperties.getSecretKey());
        log.info("[TOSS-DEBUG] Authorization  : {}", authHeader);
        log.info("[TOSS-DEBUG] Idempotency-Key: {}", idempotencyKey);
        log.info("[TOSS-DEBUG] paymentKey     : {}", request.getPaymentKey());
        log.info("[TOSS-DEBUG] orderId        : {}", request.getOrderId());
        log.info("[TOSS-DEBUG] amount         : {}", request.getAmount());
        log.info("[TOSS-DEBUG] ==========================");

        return webClient.post()
                .uri(tossPaymentProperties.getConfirmPath())
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TossPaymentResponse.class)
                .timeout(Duration.ofSeconds(paymentPolicyProperties.getApproveTimeoutSeconds()))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    public Mono<TossPaymentResponse> cancel(String paymentKey, String idempotencyKey, TossCancelRequest request) {
        return webClient.post()
                .uri(String.format(tossPaymentProperties.getCancelPathTemplate(), paymentKey))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TossPaymentResponse.class)
                .timeout(Duration.ofSeconds(paymentPolicyProperties.getCancelTimeoutSeconds()))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    private String basicAuthHeader(String secretKey) {
        String raw = secretKey + ":";
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
