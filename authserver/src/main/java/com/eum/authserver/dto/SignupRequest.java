package com.eum.authserver.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class SignupRequest {

    // 로그인용 아이디 (영문+숫자, 4~20자)
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9]+$",
            message = "아이디는 영문과 숫자만 입력 가능합니다."
    )
    private String username;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
            message = "비밀번호는 영문 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(
            regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
            message = "올바른 전화번호 형식이 아닙니다. (예: 010-1234-5678)"
    )
    private String phoneNumber;

    // ← 신규 필드: 약관 동의 정보
    // { "service_terms": true, "privacy_policy": true, "marketing_sms": false, ... }
    @NotNull(message = "약관 동의 정보는 필수입니다.")
    private Map<String, Boolean> termsAgreed;
}