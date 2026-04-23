package com.eum.productserver.common;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse {
    private String message;
    private int status;

    // 성공 응답을 위한 간단한 생성 메서드
    public static ApiResponse ok(String message) {
        return new ApiResponse(message, 200);
    }
}
