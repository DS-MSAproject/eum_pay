package com.eum.paymentserver.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * paymentserver가 Toss 취소 API를 호출할 때 사용하는 내부 요청 DTO입니다.
 * Toss에 전달할 취소 사유와 취소 금액을 구성합니다.
 */
public class TossCancelRequest {

    private String cancelReason;
    private Long cancelAmount;
}
