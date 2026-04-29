package com.eum.authserver.dto.admin;

import com.eum.authserver.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserResponse {

    private Long id;
    private String email;
    private String name;
    private String username;
    private String phoneNumber;
    private String provider;
    private String role;
    private boolean emailVerified;
    private String profileImgUrl;
    private LocalDateTime createdAt;

    public static AdminUserResponse from(User u) {
        return AdminUserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .name(u.getName())
                .username(u.getUsername())
                .phoneNumber(u.getPhoneNumber())
                .provider(u.getProvider() != null ? u.getProvider() : "local")
                .role(u.getRole() != null ? u.getRole().getKey() : "")
                .emailVerified(u.isEmailVerified())
                .profileImgUrl(u.getProfileImgUrl())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
