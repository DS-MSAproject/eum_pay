package com.eum.gatewayserver.filters;

import com.eum.gatewayserver.config.WhitelistConfig;
import com.eum.gatewayserver.jwt.JwtVerifier;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.slf4j.MDC;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {
    private static final String ACCESS_COOKIE = "accessToken";
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtVerifier                 jwtVerifier;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final WhitelistConfig             whitelistConfig;
    private final WebClient.Builder           loadBalancedWebClientBuilder;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        List<String> whitelist = whitelistConfig.getPaths();

        log.info("Current Path: {}, Whitelist: {}", path, whitelist);

        // 화이트리스트 — Ant 패턴(**, *) 지원
        if (whitelist != null && whitelist.stream()
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return chain.filter(exchange);
        }

        String accessToken = resolveAccessToken(exchange);
        Claims claims = accessToken != null ? jwtVerifier.validateAndGetClaims(accessToken) : null;

        if (claims != null && jwtVerifier.isAccessToken(claims)) {
            return authorizeRequest(exchange, chain, claims);
        }

        String refreshToken = resolveCookie(exchange, REFRESH_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) {
            return unauthorizedResponse(exchange, "인증이 만료되었습니다. 다시 로그인해주세요.");
        }

        return refreshAccessToken(exchange, refreshToken)
                .flatMap(refreshResult -> {
                    if (!refreshResult.success() || refreshResult.accessToken() == null) {
                        return unauthorizedResponse(exchange, "인증이 만료되었습니다. 다시 로그인해주세요.");
                    }

                    Claims refreshedClaims = jwtVerifier.validateAndGetClaims(refreshResult.accessToken());
                    if (refreshedClaims == null || !jwtVerifier.isAccessToken(refreshedClaims)) {
                        return unauthorizedResponse(exchange, "인증이 만료되었습니다. 다시 로그인해주세요.");
                    }

                    refreshResult.setCookies().forEach(setCookie ->
                            exchange.getResponse().getHeaders().add(HttpHeaders.SET_COOKIE, setCookie));

                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r.header(HttpHeaders.COOKIE,
                                    replaceCookieHeader(exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE),
                                            ACCESS_COOKIE, refreshResult.accessToken())))
                            .build();

                    return authorizeRequest(mutated, chain, refreshedClaims);
                })
                .onErrorResume(error -> {
                    log.warn("토큰 자동 재발급 실패: {}", error.getMessage());
                    return unauthorizedResponse(exchange, "인증이 만료되었습니다. 다시 로그인해주세요.");
                });
    }

    /**
     * 토큰 해석 (Fallback 패턴)
     *
     * 우선순위:
     * 1️⃣ Authorization 헤더에서 토큰 찾기 (Bearer)
     * 2️⃣ Authorization 헤더가 없으면 쿠키에서 찾기
     * 3️⃣ 둘 다 없으면 null 반환
     */
    private String resolveAccessToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            log.debug("토큰을 Authorization 헤더에서 획득");
            return auth.substring(7);
        }

        String accessCookie = resolveCookie(exchange, ACCESS_COOKIE);
        if (accessCookie != null && !accessCookie.isBlank()) {
            log.debug("토큰을 쿠키(accessToken)에서 획득");
            return accessCookie;
        }

        String legacyCookie = resolveCookie(exchange, "Authorization");
        if (legacyCookie != null && !legacyCookie.isBlank()) {
            log.debug("토큰을 쿠키(Authorization)에서 획득");
            return legacyCookie;
        }

        String optionalCookie = resolveCookie(exchange, "jwt_token");
        if (optionalCookie != null && !optionalCookie.isBlank()) {
            log.debug("토큰을 쿠키(jwt_token)에서 획득");
            return optionalCookie;
        }

        return null;
    }

    private String resolveCookie(ServerWebExchange exchange, String cookieName) {
        var cookies = exchange.getRequest().getCookies();
        if (!cookies.containsKey(cookieName) || cookies.getFirst(cookieName) == null) {
            return null;
        }
        return cookies.getFirst(cookieName).getValue();
    }

    private Mono<Void> authorizeRequest(ServerWebExchange exchange, WebFilterChain chain, Claims claims) {
        String path   = exchange.getRequest().getPath().value();
        String jti    = jwtVerifier.getJti(claims);
        String userId = jwtVerifier.getUserId(claims);
        String email  = jwtVerifier.getEmail(claims);
        String name   = jwtVerifier.getName(claims);
        String role   = jwtVerifier.getRole(claims);
        String displayName = (name != null && !name.isBlank()) ? name : email;
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);

        // /api/v1/admin/** 경로는 ROLE_ADMIN만 허용
        if (path.startsWith("/api/v1/admin/") && !"ROLE_ADMIN".equals(role)) {
            return forbiddenResponse(exchange, "관리자 권한이 필요합니다.");
        }

        return redisTemplate.hasKey("blacklist:" + jti)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return unauthorizedResponse(exchange, "로그아웃된 토큰입니다. 다시 로그인해주세요.");
                    }

                    MDC.put("userId", userId);
                    MDC.put("userEmail", email);

                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r
                                    .header("X-User-Id", userId)
                                    .header("X-User-Email", email)
                                    .header("X-User-Name", encodedDisplayName)
                                    .header("X-Name", encodedDisplayName)
                                    .header("X-User-Role", role)
                            )
                            .build();

                    return chain.filter(mutated).doFinally(s -> {
                        MDC.remove("userId");
                        MDC.remove("userEmail");
                    });
                });
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        var buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<RefreshResult> refreshAccessToken(ServerWebExchange exchange, String refreshToken) {
        return loadBalancedWebClientBuilder.build()
                .post()
                .uri("http://AUTHSERVER/auth/refresh")
                .header(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + refreshToken)
                .exchangeToMono(response -> {
                    List<String> setCookies = response.headers().asHttpHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);

                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(Map.class)
                                .map(body -> new RefreshResult(
                                        true,
                                        body != null ? (String) body.get("accessToken") : null,
                                        setCookies
                                ));
                    }

                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new RefreshResult(false, null, setCookies));
                });
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        expireCookie(exchange, ACCESS_COOKIE);
        expireCookie(exchange, REFRESH_COOKIE);
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = ("{\"error\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        var buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private void expireCookie(ServerWebExchange exchange, String cookieName) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .build();
        exchange.getResponse().addCookie(cookie);
    }

    private String replaceCookieHeader(String existingCookieHeader, String cookieName, String cookieValue) {
        String newCookie = cookieName + "=" + cookieValue;
        if (existingCookieHeader == null || existingCookieHeader.isBlank()) {
            return newCookie;
        }

        StringBuilder builder = new StringBuilder();
        boolean replaced = false;
        for (String cookie : existingCookieHeader.split(";")) {
            String trimmed = cookie.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                if (builder.length() > 0) builder.append("; ");
                builder.append(newCookie);
                replaced = true;
            } else if (!trimmed.isBlank()) {
                if (builder.length() > 0) builder.append("; ");
                builder.append(trimmed);
            }
        }

        if (!replaced) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(newCookie);
        }
        return builder.toString();
    }

    private record RefreshResult(boolean success, String accessToken, List<String> setCookies) {
    }
}
