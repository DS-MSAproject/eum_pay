package com.eum.gatewayserver.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVerifier {

    private final PublicKeyCache publicKeyCache;

    // 서명 + 만료 검증 후 Claims 반환
    // null이면 검증 실패 — 호출부에서 null 체크
    // 기존처럼 validate() + claims()를 따로 호출하면 같은 토큰을 5~6회 파싱하게 됨
    // 이 메서드 하나로 파싱을 1회로 줄이고 만료 경계 버그도 제거
    public Claims validateAndGetClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKeyCache.get())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰");
        } catch (SignatureException e) {
            log.debug("잘못된 서명");
        } catch (JwtException e) {
            log.debug("유효하지 않은 토큰: {}", e.getMessage());
        }
        return null;
    }

    // 아래 메서드들은 모두 Claims를 직접 받음 — 재파싱 없음
    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type", String.class));
    }

    public String getJti(Claims claims)    { return claims.getId(); }
    public String getUserId(Claims claims) { return claims.get("userId", String.class); }
    public String getEmail(Claims claims)  { return claims.getSubject(); }
    public String getRole(Claims claims)   { return claims.get("role", String.class); }
}