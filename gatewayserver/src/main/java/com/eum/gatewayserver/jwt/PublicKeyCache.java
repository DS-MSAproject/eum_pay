package com.eum.gatewayserver.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PublicKeyCache {

    @Value("${auth.jwks-uri}")
    private String jwksUri;

    private volatile RSAPublicKey cachedPublicKey;

    private final WebClient webClient;

    public PublicKeyCache(@LoadBalanced WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    // 서버 시작 시 동기적으로 공개키 초기화
    // block()을 사용해 키가 완전히 로딩된 후 서버가 요청을 받도록 보장
    // 초기화 실패 시 서버 기동 자체를 막음 (키 없는 서버 방지)
    @PostConstruct
    public void init() {
        log.info("JWKS 공개키 초기 로딩 시작: {}", jwksUri);

        // 💡 [무결성 포인트 1] 블로킹이 허용되는 스레드(boundedElastic)로 명시적 전환
        Mono.fromCallable(() -> {
                    return webClient.get()
                            .uri(jwksUri)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block(); // 👈 여기서의 block은 boundedElastic 위에서 돌아가므로 안전합니다.
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                // 💡 [무결성 포인트 2] AUTHSERVER가 뜰 때까지 듬직하게 기다리는 재시도 전략
                .retryWhen(reactor.util.retry.Retry.backoff(10, java.time.Duration.ofSeconds(5))
                        .doBeforeRetry(retrySignal -> log.warn("공개키 로딩 재시도 중... ({}회차)", retrySignal.totalRetries() + 1)))
                .doOnSuccess(jwks -> {
                    try {
                        this.cachedPublicKey = parsePublicKey(jwks);
                        log.info("JWKS 공개키 초기 로딩 완료");
                    } catch (Exception ex) {
                        throw new RuntimeException("공개키 파싱 실패", ex);
                    }
                })
                .doOnError(error -> log.error("최종 로딩 실패: {}", error.getMessage()))
                .block(); // 👈 메인 스레드는 기동 완료를 위해 여기서 딱 한 번 대기합니다.
    }

    // 주기적으로 공개키 갱신 (키 교체 대응)
    // 주기적 갱신은 비동기 — 실패해도 기존 캐시 키 유지
    @Scheduled(fixedRateString = "${auth.jwks-refresh-interval}000")
    public void refresh() {
        log.debug("JWKS 공개키 주기적 갱신 시도");
        webClient.get()
                .uri(jwksUri)
                .retrieve()
                .bodyToMono(Map.class)
                .subscribe(
                        jwks -> {
                            try {
                                this.cachedPublicKey = parsePublicKey(jwks);
                                log.info("JWKS 공개키 갱신 완료");
                            } catch (Exception ex) {
                                log.error("공개키 파싱 실패 (기존 키 유지): {}", ex.getMessage());
                            }
                        },
                        error -> log.error("JWKS 엔드포인트 접근 실패 (기존 키 유지): {}", error.getMessage())
                );
    }

    public RSAPublicKey get() {
        if (cachedPublicKey == null)
            throw new IllegalStateException("공개키가 아직 로딩되지 않았습니다.");
        return cachedPublicKey;
    }

    // 동기 로딩 — block() 사용 (PostConstruct 전용)
    private void loadPublicKey() throws Exception {
        Map jwks = webClient.get()
                .uri(jwksUri)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (jwks == null)
            throw new IllegalStateException("JWKS 응답이 비어있습니다.");

        this.cachedPublicKey = parsePublicKey(jwks);
    }

    // JWKS Map → RSAPublicKey 파싱 (init / refresh 공통 사용)
    private RSAPublicKey parsePublicKey(Map<?, ?> jwks) throws Exception {
        List<?> keys = (List<?>) jwks.get("keys");
        if (keys == null || keys.isEmpty())
            throw new IllegalStateException("JWKS에 키가 없습니다.");

        Map<?, ?> key = (Map<?, ?>) keys.get(0);

        byte[] n = Base64.getUrlDecoder().decode((String) key.get("n"));
        byte[] e = Base64.getUrlDecoder().decode((String) key.get("e"));

        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, n),
                new BigInteger(1, e)
        );

        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}