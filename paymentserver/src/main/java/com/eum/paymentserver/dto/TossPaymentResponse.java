package com.eum.paymentserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Toss 승인/취소 API 응답을 역직렬화하기 위한 DTO입니다.
 * paymentserver는 이 값을 읽어 결제 상태와 결제수단 정보를 반영합니다.
 */
public class TossPaymentResponse {

    private String paymentKey;
    private String status;
    private String method;
    private String approvedAt;
    private String orderId;
    private EasyPay easyPay;
    private Cancels[] cancels;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EasyPay {
        private String provider;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cancels {
        private String transactionKey;
        private String canceledAt;
        private String cancelReason;
    }
}
