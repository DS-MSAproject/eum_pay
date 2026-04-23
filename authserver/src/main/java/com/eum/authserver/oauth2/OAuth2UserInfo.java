package com.eum.authserver.oauth2;

public interface OAuth2UserInfo {
    String getProviderId();
    String getProvider();
    String getEmail();
    String getName();
    String getProfileImg();
    String getPhoneNumber(); // 네이버만 제공, 구글/카카오는 null
}
