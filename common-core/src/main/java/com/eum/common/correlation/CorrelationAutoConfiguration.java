package com.eum.common.correlation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CorrelationAutoConfiguration {

    @Bean
    public CorrelationAspect correlationAspect() {
        return new CorrelationAspect();
    }
}
