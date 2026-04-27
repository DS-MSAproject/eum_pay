package com.eum.paymentserver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 프론트가 결제창을 띄우기 전에 prepare API로 보내는 입력 DTO입니다.
 * 주문 식별값과 Toss 위젯 표시용 기본 정보를 담습니다.
 */
public class PreparePaymentRequest {

    @NotNull
    private Long orderId;

    @NotBlank
    private String orderName;

    @NotNull
    @Min(1)
    private Long amount;

    private String customerName;

    @Email
    private String customerEmail;

    private String currency = "KRW";
}
