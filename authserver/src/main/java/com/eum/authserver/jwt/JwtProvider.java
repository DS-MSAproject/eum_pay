package com.eum.authserver.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final RSAPrivateKey rsaPrivateKey;
    private final RSAPublicKey  rsaPublicKey;

    @Value("${jwt.access-token-expire}")
    private long accessTokenExpire;

    // ── Access Token 생성 ─────────────────────────────
    public String createAccessToken(Long userId, String email, String role, String name) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim("userId", userId.toString())
                .claim("name",   name)
                .claim("role",   role)
                .claim("type",   "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpire))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    // ── Refresh Token 생성 ────────────────────────────
    public String createRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ── 서명 검증 ─────────────────────────────────────
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("잘못된 JWT 서명: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("형식이 잘못된 JWT: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT가 비어있음: {}", e.getMessage());
        }
        return false;
    }

    // ── 클레임 추출 ───────────────────────────────────
    public String getEmail(String token)      { return parseClaims(token).getSubject(); }
    public String getUserId(String token)     { return parseClaims(token).get("userId", String.class); }
    public String getName(String token)       { return parseClaims(token).get("name",   String.class); }
    public String getRole(String token)       { return parseClaims(token).get("role",   String.class); }
    public String getJti(String token)        { return parseClaims(token).getId(); }
    public Date   getExpiration(String token) { return parseClaims(token).getExpiration(); }

    public boolean isAccessToken(String token) {
        return "access".equals(parseClaims(token).get("type", String.class));
    }

    public RSAPublicKey getPublicKey() { return rsaPublicKey; }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(rsaPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
