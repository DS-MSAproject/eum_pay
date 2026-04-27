package com.eum.authserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Configuration
public class RsaKeyConfig {

    @Value("${vault.enabled}")
    private boolean vaultEnabled;

    @Value("${vault.uri:}")
    private String vaultUri;

    @Value("${vault.token:}")
    private String vaultToken;

    // application.yml: vault.path=secret/auth/rsa
    @Value("${vault.path:}")
    private String vaultPath;

    @Value("${jwt.private-key:}")
    private String hardcodedPrivateKey;

    @Value("${jwt.public-key:}")
    private String hardcodedPublicKey;

    @Bean
    public RSAPrivateKey rsaPrivateKey() throws Exception {
        String pem = vaultEnabled ? fetchFromVault("private_key") : hardcodedPrivateKey;
        return parsePrivateKey(pem);
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        String pem = vaultEnabled ? fetchFromVault("public_key") : hardcodedPublicKey;
        return parsePublicKey(pem);
    }

    private String fetchFromVault(String field) {
        log.info("Vault에서 키 로딩: {}/{}", vaultPath, field);

        RestClient client = RestClient.builder()
                .baseUrl(vaultUri)
                .defaultHeader("X-Vault-Token", vaultToken)
                .build();

        // KV v2는 mount 다음에 "data/"가 필요
        // secret/auth/rsa → /v1/secret/data/auth/rsa
        String kvPath = vaultPath.replaceFirst("^([^/]+)/", "$1/data/");

        Map<?, ?> response = client.get()
                .uri("/v1/" + kvPath)
                .retrieve()
                .body(Map.class);

        // KV v2 응답 구조: { data: { data: { private_key: "..." } } }
        Map<?, ?> outer = (Map<?, ?>) response.get("data");
        Map<?, ?> inner = (Map<?, ?>) outer.get("data");
        return (String) inner.get(field);
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}