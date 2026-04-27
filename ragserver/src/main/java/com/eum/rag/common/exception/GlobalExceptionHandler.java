package com.eum.rag.common.exception;

import com.eum.rag.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business exception: code={}, message={}", errorCode.getCode(), exception.getMessage());

        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(Exception exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        log.warn("Validation exception: message={}", exception.getMessage());

        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("Unhandled exception", exception);

        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }
}
