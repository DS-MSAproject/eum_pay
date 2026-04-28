package com.eum.searchserver.dto.response;

import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 스토어 탭 눌렀을때
 */
@Builder
public record ProductSearchResponse(
        Long id,                // 상품 고유 ID
        String imageUrl,        // 상품 대표 이미지 URL
        String productTitle,    // 상품명 (예: 오독오독 바삭 캥거루 120g)
        String productInfo,     // 상품 텍스트 상세 정보(추후 정식 필드)
        String content,         // 기존 content 기반 상세 설명(하위 호환)

        // 💰 가격 관련 필드
        Long originalPrice,  // 정가 (예: 47,700) -> UI에서 취소선 처리용
        Long price,          // 최종 판매가 혹은 할인가 (예: 45,310) -> 메인 노출 가격
        Double discountRate,    // 할인율 (예: 0.05 -> 5%) -> "5% ↓" 표현용

        // 🏷️ 배지 및 태그 관련 필드
        String discountTag,     // 💡 초록색 배지명 (예: "최적할인가")
        Boolean isNew,          // 💡 주황색 [NEW] 배지 노출 여부
        String productTag,      // 💡 랭킹 배지 (예: "[판매 1위]", "BEST")

        String productUrl,      // 상세 페이지 이동 경로
        String category        // 카테고리 (필터링 확인용)
) {}
