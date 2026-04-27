package com.eum.common.correlation;

import java.util.Optional;

public final class CorrelationContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void set(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(correlationId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static String getOrElse(String fallback) {
        return Optional.ofNullable(HOLDER.get())
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
