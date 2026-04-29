package com.eum.authserver.dto;

import com.eum.authserver.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AdminMeResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final String role;

    public static AdminMeResponse from(User user) {
        return new AdminMeResponse(
                user.getId(),
                user.getEmail(),
                user.getName() != null ? user.getName() : user.getUsername(),
                user.getRole().getKey()
        );
    }
}
