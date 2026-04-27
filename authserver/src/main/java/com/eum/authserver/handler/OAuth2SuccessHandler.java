package com.eum.authserver.handler;

import com.eum.authserver.dto.TokenPair;
import com.eum.authserver.entity.User;
import com.eum.authserver.oauth2.OAuth2UserInfo;
import com.eum.authserver.oauth2.OAuth2UserInfoFactory;
import com.eum.authserver.repository.SocialAccountRepository;
import com.eum.authserver.repository.UserRepository;
import com.eum.authserver.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private static final String ACCESS_COOKIE = "accessToken";
    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService             authService;
    private final UserRepository          userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Value("${app.frontend-url:http://localhost:3001}")
    private String frontendUrl;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = authToken.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory
                .getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());

        String clientIp  = resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        User user = socialAccountRepository
                .findByProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
                .map(sa -> {
                    Long userId = sa.getUser().getId();
                    return userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "소셜 로그인 유저를 찾을 수 없습니다: " + userId));
                })
                .orElseGet(() -> userRepository.findByEmail(userInfo.getEmail())
                        .orElseThrow(() -> new IllegalStateException(
                                "OAuth2 유저를 찾을 수 없습니다: " + userInfo.getEmail())));

        // 로그인 이력 저장 포함 토큰 발급
        TokenPair tokens = authService.issueWithHistory(
                user, clientIp, userAgent, userInfo.getProvider());

        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_COOKIE, tokens.getAccessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofMinutes(15))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE, tokens.getRefreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/");
    }

    // X-Forwarded-For 헤더 우선 (Gateway 뒤 실제 IP)
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
