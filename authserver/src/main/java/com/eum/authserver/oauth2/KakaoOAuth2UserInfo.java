package com.eum.authserver.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes  = attributes;
        // kakao_account 안에 이메일, 프로필 정보가 있음
        Object account = attributes.get("kakao_account");
        this.kakaoAccount = (account instanceof Map) ? (Map<String, Object>) account : Map.of();

        // profile 안에 닉네임, 프로필 사진이 있음
        Object prof = kakaoAccount.get("profile");
        this.profile = (prof instanceof Map) ? (Map<String, Object>) prof : Map.of();
    }

    @Override
    public String getProviderId() {
        // 카카오 고유 ID는 최상위 id 필드 (Long 타입)
        Object id = attributes.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getEmail() {
        // 이메일 동의 여부 확인 후 반환
        Boolean emailAgreed = (Boolean) kakaoAccount.get("email_needs_agreement");
        if (Boolean.TRUE.equals(emailAgreed)) return null; // 동의 안 한 경우
        return (String) kakaoAccount.get("email");
    }

    @Override
    public String getName() {
        // 닉네임 사용
        return (String) profile.get("nickname");
    }

    @Override
    public String getProfileImg() {
        // 프로필 사진 URL
        return (String) profile.get("profile_image_url");
    }

    @Override
    public String getPhoneNumber() {
        // 카카오는 일반 OAuth2로 전화번호 제공 안 함
        return null;
    }
}