package com.eum.rag.common.config;

import com.eum.rag.common.async.RagAsyncExceptionHandler;
import com.eum.rag.common.config.properties.RagAsyncProperties;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private final RagAsyncExceptionHandler ragAsyncExceptionHandler;

    public AsyncConfig(RagAsyncExceptionHandler ragAsyncExceptionHandler) {
        this.ragAsyncExceptionHandler = ragAsyncExceptionHandler;
    }

    @Bean(name = "indexingExecutor")
    public ThreadPoolTaskExecutor indexingExecutor(RagAsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-async-default-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return ragAsyncExceptionHandler;
    }
}
