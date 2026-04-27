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
@ConfigurationProperties(prefix = "payment.policy")
public class PaymentPolicyProperties {

    private int approveTimeoutSeconds = 10;
    private int cancelTimeoutSeconds = 10;
    private int compensationMaxRetry = 5;
    private long compensationBackoffMs = 2000L;
}
