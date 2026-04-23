package com.eum.authserver.controller;

import com.eum.authserver.dto.LoginRequest;
import com.eum.authserver.dto.SignupRequest;
import com.eum.authserver.dto.TokenPair;
import com.eum.authserver.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String ACCESS_COOKIE = "accessToken";
    private static final String REFRESH_COOKIE = "refreshToken";

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    // ── 회원가입 ──────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(
            @RequestBody @Valid SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String clientIp  = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // ← IP, UserAgent 함께 전달 (약관 저장 시 사용)
        TokenPair tokens = authService.signup(request, clientIp, userAgent);

        addAccessCookie(response, tokens.getAccessToken());
        addRefreshCookie(response, tokens.getRefreshToken());
        return ResponseEntity.ok(Map.of(
                "accessToken", tokens.getAccessToken()
        ));
    }

    // ── 아이디 로그인 ─────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String clientIp  = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        TokenPair tokens = authService.login(
                request.getUsername(), request.getPassword(), clientIp, userAgent);

        addAccessCookie(response, tokens.getAccessToken());
        addRefreshCookie(response, tokens.getRefreshToken());
        return ResponseEntity.ok(Map.of("accessToken", tokens.getAccessToken()));
    }

    // ── Silent Refresh ────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null)
            return ResponseEntity.status(401).build();

        try {
            TokenPair tokens = authService.refreshByToken(refreshToken);
            addAccessCookie(response, tokens.getAccessToken());
            addRefreshCookie(response, tokens.getRefreshToken());
            return ResponseEntity.ok(Map.of("accessToken", tokens.getAccessToken()));
        } catch (IllegalArgumentException e) {
            expireAccessCookie(response);
            expireRefreshCookie(response);
            return ResponseEntity.status(401).build();
        }
    }

    // ── 로그아웃 ──────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(name = ACCESS_COOKIE, required = false) String accessTokenCookie,
            HttpServletResponse response) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logoutByToken(authHeader.substring(7));
        } else if (accessTokenCookie != null && !accessTokenCookie.isBlank()) {
            authService.logoutByToken(accessTokenCookie);
        }
        expireAccessCookie(response);
        expireRefreshCookie(response);
        return ResponseEntity.ok().build();
    }

    // ── 클라이언트 IP 추출 ────────────────────────────
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ── 쿠키 헬퍼 ────────────────────────────────────
    private void addAccessCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofMinutes(15))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void expireAccessCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void expireRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
