package com.eum.rag.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeminiResilienceExecutor {

    private final CircuitBreaker geminiCircuitBreaker;
    private final Retry geminiRetry;

    public <T> T execute(Callable<T> callable) {
        Callable<T> protectedCall = Retry.decorateCallable(geminiRetry, CircuitBreaker.decorateCallable(geminiCircuitBreaker, callable));
        try {
            return protectedCall.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
