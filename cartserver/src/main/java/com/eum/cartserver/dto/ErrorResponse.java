package com.eum.cartserver.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    private int status;
    private String code;
    private String message;
    private Map<String, String> errors;

    public static ErrorResponse of(int status, String code, String message) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .build();
    }

    public static ErrorResponse of(int status, String code, String message, Map<String, String> errors) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .errors(errors)
                .build();
    }
}
