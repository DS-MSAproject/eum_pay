package com.eum.paymentserver.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 프론트가 Toss 위젯 결제 후 최종 승인을 요청할 때 보내는 입력 DTO입니다.
 * Toss에서 받은 paymentKey, orderId, amount를 전달합니다.
 */
public class ConfirmPaymentRequest {

    @NotBlank
    private String paymentKey;

    @NotNull
    private Long orderId;

    @NotNull
    @Min(1)
    private Long amount;
}
