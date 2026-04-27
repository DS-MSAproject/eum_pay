package com.eum.authserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> errors
) {
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, null);
    }

    public static ErrorResponse of(int status, String code, String message, Map<String, String> errors) {
        return new ErrorResponse(status, code, message, errors);
    }
}
