package com.eum.authserver.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    // 현재 서비스 정책상 구매자(USER)만 지원합니다.
    USER("ROLE_USER", "일반 사용자");

    private final String key;
    private final String title;
}
