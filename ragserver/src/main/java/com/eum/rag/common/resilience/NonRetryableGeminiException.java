package com.eum.rag.common.resilience;

public class NonRetryableGeminiException extends RuntimeException {

    public NonRetryableGeminiException(String message) {
        super(message);
    }

    public NonRetryableGeminiException(String message, Throwable cause) {
        super(message, cause);
    }
}
