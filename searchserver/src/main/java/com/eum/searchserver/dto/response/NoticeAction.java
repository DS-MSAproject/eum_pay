package com.eum.searchserver.dto.response;

// 공지 제목 옆 +를 클릭했을 때
public record NoticeAction(
        String label,      // 버튼에 표시될 텍스트 (예: "쿠폰 받기", "상품 보기")
        String targetUrl,  // 이동할 URL (Internal or External)
        String actionType, // 💡 [추가] "LINK"(이동), "COUPON"(기능 실행) 등 구분용
        Integer sortOrder  // 버튼이 여러 개일 때의 노출 순서
) {}