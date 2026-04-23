package com.eum.paymentserver.dto;

import com.eum.paymentserver.domain.CancelReasonType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 프론트가 결제 취소를 요청할 때 보내는 입력 DTO입니다.
 * 취소 사유, 사유 유형, 취소 금액을 담습니다.
 */
public class CancelPaymentRequest {

    @NotBlank
    private String reason;

    @NotNull
    private CancelReasonType reasonType;

    @NotNull
    @Min(1)
    private Long cancelAmount;
}
