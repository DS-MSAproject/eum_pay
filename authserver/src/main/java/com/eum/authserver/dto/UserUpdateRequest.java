package com.eum.authserver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequest {

    // 회원정보 수정 화면에서 읽기 전용으로 사용한다.
    // 프론트가 보내더라도 서버에서는 수정하지 않는다.
    private String name;

    @Pattern(
            regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
            message = "올바른 전화번호 형식이 아닙니다."
    )
    private String phoneNumber;

    // 회원정보 수정 화면에서 읽기 전용으로 사용한다.
    // 프론트가 보내더라도 서버에서는 수정하지 않는다.
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    // 비밀번호 변경 (선택)
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    // 마케팅 수신 동의
    private MarketingConsent marketingConsent;

    @Getter
    @NoArgsConstructor
    public static class MarketingConsent {
        private Boolean smsAllowed;
        private Boolean emailAllowed;
    }
}
