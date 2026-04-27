package com.eum.searchserver.dto.response;

/**
 * 자동완성 응답을 위한 DTO
 * Record를 사용하면 getter, equals, hashCode가 자동으로 생성되어
 * distinct() 처리나 JSON 변환에 매우 유리합니다.
 */
public record AutocompleteResponse(
        Long id,        // 클릭 시 상세 페이지 이동을 위해 필요
        String title    // 화면에 표시할 상품명
) {}
