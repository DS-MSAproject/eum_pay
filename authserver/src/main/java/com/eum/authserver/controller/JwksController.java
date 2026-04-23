package com.eum.authserver.controller;

import com.eum.authserver.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtProvider jwtProvider;

    // Gateway가 시작 시 이 엔드포인트를 호출해 공개키를 캐싱
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = jwtProvider.getPublicKey();

        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", "gopang-auth-key-1",
                "n", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(pub.getModulus().toByteArray()),
                "e", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(pub.getPublicExponent().toByteArray())
        )));
    }
}
