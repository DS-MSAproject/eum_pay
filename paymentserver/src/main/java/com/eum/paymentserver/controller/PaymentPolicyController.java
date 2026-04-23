package com.eum.paymentserver.controller;

import com.eum.paymentserver.config.PaymentPolicyProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
@RequestMapping("/payments/config")
public class PaymentPolicyController {

    private final PaymentPolicyProperties paymentPolicyProperties;

    public PaymentPolicyController(PaymentPolicyProperties paymentPolicyProperties) {
        this.paymentPolicyProperties = paymentPolicyProperties;
    }

    @GetMapping("/policy")
    public Map<String, Object> getPolicy() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approveTimeoutSeconds", paymentPolicyProperties.getApproveTimeoutSeconds());
        response.put("cancelTimeoutSeconds", paymentPolicyProperties.getCancelTimeoutSeconds());
        response.put("compensationMaxRetry", paymentPolicyProperties.getCompensationMaxRetry());
        response.put("compensationBackoffMs", paymentPolicyProperties.getCompensationBackoffMs());
        return response;
    }
}
