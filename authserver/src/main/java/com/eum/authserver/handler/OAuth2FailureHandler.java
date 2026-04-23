package com.eum.authserver.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend-url:http://localhost:3001}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException {

        String errorMessage = "소셜 로그인에 실패했습니다.";

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String code = oauthEx.getError().getErrorCode();
            if ("email_already_exists".equals(code)) {
                errorMessage = oauthEx.getMessage();
            }
        }

        log.warn("OAuth2 로그인 실패: {}", errorMessage);

        // 에러 메시지를 URL 파라미터로 프론트에 전달
        String encoded = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/login?error=" + encoded);
    }
}
