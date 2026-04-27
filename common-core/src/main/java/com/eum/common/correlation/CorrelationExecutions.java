package com.eum.common.correlation;

import java.util.function.Supplier;
import org.slf4j.MDC;

public final class CorrelationExecutions {

    private CorrelationExecutions() {
    }

    public static <T> T callWith(String correlationId, Supplier<T> supplier) {
        String previousContext = CorrelationContext.get();
        String previousMdc = MDC.get("correlationId");
        String resolved = CorrelationIdResolver.resolveOrGenerate(correlationId);

        CorrelationContext.set(resolved);
        MDC.put("correlationId", resolved);
        try {
            return supplier.get();
        } finally {
            restore(previousContext, previousMdc);
        }
    }

    public static void runWith(String correlationId, Runnable runnable) {
        callWith(correlationId, () -> {
            runnable.run();
            return null;
        });
    }

    private static void restore(String previousContext, String previousMdc) {
        if (previousContext == null || previousContext.isBlank()) {
            CorrelationContext.clear();
        } else {
            CorrelationContext.set(previousContext);
        }

        if (previousMdc == null || previousMdc.isBlank()) {
            MDC.remove("correlationId");
        } else {
            MDC.put("correlationId", previousMdc);
        }
    }
}
