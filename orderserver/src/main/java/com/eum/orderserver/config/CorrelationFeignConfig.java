package com.eum.orderserver.config;

import com.eum.common.correlation.CorrelationConstants;
import com.eum.common.correlation.CorrelationIdResolver;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CorrelationFeignConfig {

    @Bean
    public RequestInterceptor correlationIdRequestInterceptor() {
        return template -> template.header(
                CorrelationConstants.CORRELATION_HEADER,
                CorrelationIdResolver.resolveOrGenerate(null)
        );
    }
}
