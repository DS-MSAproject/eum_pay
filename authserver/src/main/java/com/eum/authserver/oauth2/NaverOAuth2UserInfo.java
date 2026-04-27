package com.eum.authserver.oauth2;

import java.util.Map;

public class NaverOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        Object response = attributes.get("response");
        this.attributes = (response instanceof Map)
                ? (Map<String, Object>) response
                : Map.of();
    }

    @Override
    public String getProviderId() {
        if (attributes.isEmpty()) return null;
        return (String) attributes.get("id");
    }

    @Override
    public String getProvider() {
        return "naver";
    }

    @Override
    public String getEmail() {
        if (attributes.isEmpty()) return null;
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        if (attributes.isEmpty()) return null;
        return (String) attributes.get("name");
    }

    @Override
    public String getProfileImg() {
        if (attributes.isEmpty()) return null;
        return (String) attributes.get("profile_image");
    }

    @Override
    public String getPhoneNumber() {
        if (attributes.isEmpty()) return null;
        // 네이버 전화번호 형식: 010-1234-5678
        return (String) attributes.get("mobile");
    }
}