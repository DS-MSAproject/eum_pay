package com.eum.authserver.config;

import com.eum.authserver.handler.OAuth2FailureHandler;
import com.eum.authserver.handler.OAuth2SuccessHandler;
import com.eum.authserver.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler    oAuth2SuccessHandler;
    private final OAuth2FailureHandler    oAuth2FailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.disable())        // Gateway에서 처리
                .csrf(csrf -> csrf.disable())         // Gateway에서 처리
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .headers(h -> h.frameOptions(f -> f.disable()))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(auth -> auth
                                .anyRequest().permitAll() // 인증은 Gateway가 처리
                        // 유저 식별은 X-User-Email 헤더로
                )
                // OAuth2 소셜 로그인 — Spring Security 내부 처리 필수
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                );

        return http.build();
    }
}