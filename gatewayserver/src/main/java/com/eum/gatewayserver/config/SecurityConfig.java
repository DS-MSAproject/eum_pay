package com.eum.gatewayserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.allowed-origins:https://localhost:5173,https://localhost}")
    private List<String> allowedOrigins;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    private static final ServerWebExchangeMatcher CSRF_WHITELIST =
            new OrServerWebExchangeMatcher(
                    ServerWebExchangeMatchers.matchers(
                            exchange -> {
                                HttpMethod method = exchange.getRequest().getMethod();
                                boolean isSafe = method == HttpMethod.GET
                                        || method == HttpMethod.HEAD
                                        || method == HttpMethod.OPTIONS
                                        || method == HttpMethod.TRACE;
                                return isSafe
                                        ? ServerWebExchangeMatcher.MatchResult.match()
                                        : ServerWebExchangeMatcher.MatchResult.notMatch();
                            }
                    ),
                    ServerWebExchangeMatchers.pathMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/signup",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/oauth/token",
                            "/api/v1/auth/email/send",
                            "/api/v1/auth/email/verify",
                            "/api/v1/oauth2/**",
                            "/api/v1/login/oauth2/**",
                            "/.well-known/**",
                            "/api/v1/csrf",
                            "/actuator/**",
                            "/api/media/**"
                    )
            );

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 💡 1. [핵심 추가] Actuator 경로는 HTTPS 리다이렉트 대상에서 제외
                // 프로메테우스가 HTTP로 찔렀을 때 301 리다이렉트를 받지 않도록 격리합니다.
                .redirectToHttps(redirect -> redirect
                        .httpsRedirectWhen(exchange -> {
                            String path = exchange.getRequest().getPath().value();
                            return !path.startsWith("/actuator"); // actuator가 아니면 리다이렉트
                        })
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        // CsrfTokenRequestAttributeHandler — Spring Security 6.x 최신
                        // Double Submit Cookie 방식에서 토큰값이 고정되어야 함
                        // XorServerCsrfTokenRequestAttributeHandler는 매 요청마다
                        // 토큰값이 달라져서 쿠키값과 불일치 발생 → 제거
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(
                                new NegatedServerWebExchangeMatcher(CSRF_WHITELIST)
                        )
                )
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyServerHttpHeadersWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=(), payment=()"))
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().permitAll())
                .build();
    }

    // WebFlux에서도 지연 로딩된 CsrfToken을 실제로 생성하게 만들어
    // 응답에 XSRF-TOKEN 쿠키가 바로 실리도록 한다.
    @Bean
    public WebFilter csrfCookieWebFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
            if (csrfToken == null) {
                return chain.filter(exchange);
            }

            return csrfToken
                    .doOnSuccess(token -> {
                        if (token == null) {
                            return;
                        }
                        // getToken() 호출로 Spring Security의 지연 생성 로직을 깨운다.
                        String tokenValue = token.getToken();
                        ResponseCookie cookie = ResponseCookie
                                .from("XSRF-TOKEN", tokenValue)
                                .path("/")
                                .httpOnly(false)
                                .secure(cookieSecure)
                                .sameSite(cookieSameSite)
                                .build();
                        exchange.getResponse().getCookies().set("XSRF-TOKEN", cookie);
                    })
                    .then(chain.filter(exchange));
        };
    }

    @Bean
    public CookieServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repo =
                CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        return repo;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-XSRF-TOKEN",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));
        config.setExposedHeaders(List.of(
                "Authorization",
                "X-XSRF-TOKEN"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
