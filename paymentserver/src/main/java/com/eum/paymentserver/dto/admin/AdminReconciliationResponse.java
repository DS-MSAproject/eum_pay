package com.eum.paymentserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminReconciliationResponse {

    private long totalPaid;          // APPROVED 상태 결제 합계
    private long totalRefunded;      // 취소 완료(DONE) 금액 합계
    private long netRevenue;         // totalPaid - totalRefunded
    private long discrepancyCount;   // FAILED + CANCEL_FAILED 건수
}
