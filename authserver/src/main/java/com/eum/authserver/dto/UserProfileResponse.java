package com.eum.authserver.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {

    private String status;
    private Data data;

    @Getter
    @Builder
    public static class Data {
        private String userId;       // username
        private String name;
        private String email;
        private String phoneNumber;
        private boolean smsAllowed;
        private boolean emailAllowed;
        private LocalDateTime updatedAt;
    }
}