package com.eum.rag.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-400", "Invalid request."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON-401", "Validation failed."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "Resource not found."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOC-415", "Unsupported file type."),
    DOCUMENT_PARSE_ERROR(HttpStatus.BAD_REQUEST, "DOC-422", "Failed to parse the uploaded document."),
    EMBEDDING_ERROR(HttpStatus.BAD_GATEWAY, "EMB-502", "Failed to generate embedding."),
    INDEXING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "IDX-500", "Failed to index document chunks."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SES-404", "Session not found."),
    LLM_GENERATION_ERROR(HttpStatus.BAD_GATEWAY, "LLM-502", "Failed to generate answer from LLM."),
    QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI-429", "Gemini API quota exceeded. Please try again later."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "Unexpected internal error.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
