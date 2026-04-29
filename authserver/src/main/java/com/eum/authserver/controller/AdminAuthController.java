package com.eum.authserver.controller;

import com.eum.authserver.dto.AdminLoginRequest;
import com.eum.authserver.dto.AdminMeResponse;
import com.eum.authserver.dto.TokenPair;
import com.eum.authserver.entity.Role;
import com.eum.authserver.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    private static final String ACCESS_COOKIE  = "accessToken";
    private static final String REFRESH_COOKIE = "refreshToken";

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${admin.account.email}")
    private String adminEmail;

    @Value("${admin.account.name}")
    private String adminName;

    // ── 관리자 로그인 ─────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody @Valid AdminLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String clientIp  = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        TokenPair tokens = authService.adminLogin(
                request.getEmail(), request.getPassword(), clientIp, userAgent);

        addAccessCookie(response, tokens.getAccessToken());
        addRefreshCookie(response, tokens.getRefreshToken());
        return ResponseEntity.ok(Map.of("accessToken", tokens.getAccessToken()));
    }

    // ── 관리자 로그아웃 ───────────────────────────────
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

    // ── 현재 관리자 정보 조회 ──────────────────────────
    // Gateway가 JWT 검증 후 X-User-Email, X-User-Role 헤더를 주입
    @GetMapping("/me")
    public ResponseEntity<AdminMeResponse> me(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        if (email == null || !Role.ADMIN.getKey().equals(role)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(new AdminMeResponse(0L, adminEmail, adminName, Role.ADMIN.getKey()));
    }

    // ── 헬퍼 ──────────────────────────────────────────
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void addAccessCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/").maxAge(Duration.ofMinutes(15)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/").maxAge(Duration.ofDays(7)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void expireAccessCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void expireRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
