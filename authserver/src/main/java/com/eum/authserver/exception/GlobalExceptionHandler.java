package com.eum.authserver.exception;

import com.eum.authserver.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // @Valid 검증 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        "입력값이 올바르지 않습니다.",
                        errors
                )
        );
    }

    // @Validated + request param/path variable 검증 실패 처리
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".")
                    ? path.substring(path.lastIndexOf('.') + 1)
                    : path;
            errors.put(field, violation.getMessage());
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        "요청값이 올바르지 않습니다.",
                        errors
                )
        );
    }

    // 비즈니스 로직 예외 처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.info("[AUTH_EVENT] BAD_REQUEST | Message: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "BAD_REQUEST",
                        ex.getMessage()
                )
        );
    }

    // 계정 잠금 예외 처리
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex) {

        log.warn("[AUTH_EVENT] ACCOUNT_LOCKED | Remain: {}s", ex.getRemainSeconds());

        return ResponseEntity.status(HttpStatus.LOCKED).body(
                ErrorResponse.of(
                        HttpStatus.LOCKED.value(),
                        "ACCOUNT_LOCKED",
                        ex.getMessage(),
                        Map.of("remainSeconds", String.valueOf(ex.getRemainSeconds()))
                )
        );
    }

    // JSON 형식이 잘못된 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_JSON",
                        "요청 본문(JSON) 형식이 올바르지 않습니다."
                )
        );
    }

    // Content-Type 오류
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "UNSUPPORTED_MEDIA_TYPE",
                        "지원하지 않는 Content-Type 입니다. application/json으로 요청해주세요."
                )
        );
    }
}
