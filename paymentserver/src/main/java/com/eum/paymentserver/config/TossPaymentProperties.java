package com.eum.paymentserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "payment.toss")
public class TossPaymentProperties {

    private String baseUrl = "https://api.tosspayments.com";
    private String secretKey;
    private String confirmPath = "/v1/payments/confirm";
    private String cancelPathTemplate = "/v1/payments/%s/cancel";
}
