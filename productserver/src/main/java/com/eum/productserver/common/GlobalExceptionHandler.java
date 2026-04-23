package com.eum.productserver.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice // 모든 컨트롤러에서 발생하는 에러를 여기서 잡습니다.
public class GlobalExceptionHandler {

    // 1. 우리가 직접 던지는 예외 (본인 확인 실패 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("비즈니스 로직 에러: {}", e.getMessage());

        // Map 대신 ApiResponse를 사용합니다.
        ApiResponse response = new ApiResponse(e.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse> handleMissingParams(MissingServletRequestParameterException e) {
        log.error("필수 값 누락 (로그인 정보 의심): {}", e.getParameterName());

        // 유저가 이해하기 쉽게 메시지 변경
        String message = String.format("요청 정보가 부족합니다(%s). 로그인이 되어 있는지 확인해주세요.", e.getParameterName());

        ApiResponse response = new ApiResponse(message, 401);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. 그 외 예상치 못한 모든 에러 (500 에러 처리)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleAllException(Exception e) {
        log.error("서버 내부 에러: ", e); // 스택트레이스 포함 로그

        ApiResponse response = new ApiResponse("서버 내부 오류가 발생했습니다.", 401);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // 4. @Valid 검증 실패 시 (상품명 50자 초과, 필수값 누락 등)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationExceptions(MethodArgumentNotValidException e) {
        // 에러 메시지들 중 첫 번째 것만 가져옵니다 (예: "상품명은 최대 50자까지 가능합니다.")
        String errorMessage = e.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();

        log.error("검증 실패 에러: {}", errorMessage);

        // 팀 규칙(401 코드)에 맞춰 ApiResponse 생성
        ApiResponse response = new ApiResponse(errorMessage, 401);

        // 상태 코드는 BAD_REQUEST(400)로 보내는 것이 정석이지만,
        // 팀 방침이 401 통일이라면 그대로 유지하시면 됩니다.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}