package com.eum.reviewserver.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthUserGrpcResponse {
    private Long userId;
    private String username;
    private String email;
    private String name;
    private String role;
    private String provider;
    private boolean emailVerified;
}
