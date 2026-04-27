package com.eum.authserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class SocialSignupRequest {

    // 소셜 로그인 성공 후 받은 임시 토큰
    @NotBlank(message = "tempToken은 필수입니다.")
    private String tempToken;

    // 약관 동의 정보
    // { "service_terms": true, "privacy_policy": true, "marketing_sms": true, ... }
    @NotNull(message = "약관 동의 정보는 필수입니다.")
    private Map<String, Boolean> termsAgreed;
}