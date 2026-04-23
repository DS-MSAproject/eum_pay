package com.eum.authserver.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileResponse {

    private String status;
    private Data data;

    @Getter
    @Builder
    public static class Data {
        private UserSummary userSummary;
        private Benefits benefits;
        private OrderStatusSummary orderStatusSummary;
        private ActivityCounts activityCounts;
    }

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
        private String greetingMessage;
        private String membershipLevel;
    }

    @Getter
    @Builder
    public static class Benefits {
        private int points;
        private int couponCount;       // 추후 couponserver 연동
        private int orderTotalCount;   // 추후 orderserver 연동
    }

    @Getter
    @Builder
    public static class OrderStatusSummary {
        private String recentPeriod;
        private MainStatuses mainStatuses;
        private SubStatuses subStatuses;
    }

    @Getter
    @Builder
    public static class MainStatuses {
        private int pendingPayment;   // 입금전
        private int preparing;        // 배송준비중
        private int shipping;         // 배송중
        private int delivered;        // 배송완료
    }

    @Getter
    @Builder
    public static class SubStatuses {
        private int cancelled;  // 취소
        private int exchanged;  // 교환
        private int returned;   // 반품
    }

    @Getter
    @Builder
    public static class ActivityCounts {
        private int wishlistCount;        // 추후 productserver 연동
        private int postCount;            // 추후 boardserver 연동
        private int regularDeliveryCount; // 보류
    }
}