package com.eum.common.correlation;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class CorrelationIdResolver {

    private CorrelationIdResolver() {
    }

    public static String resolveOrGenerate(String explicitValue) {
        return resolve(explicitValue).orElseGet(() -> UUID.randomUUID().toString());
    }

    public static Optional<String> resolve(String explicitValue) {
        return nonBlank(explicitValue)
                .or(() -> nonBlank(CorrelationContext.get()))
                .or(() -> nonBlank(MDC.get("correlationId")))
                .or(CorrelationIdResolver::fromServletHeader);
    }

    public static String fromSourceObject(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof String value) {
            return value;
        }
        try {
            Method getter = source.getClass().getMethod("getCorrelationId");
            Object value = getter.invoke(source);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = source.getClass().getDeclaredField("correlationId");
            field.setAccessible(true);
            Object value = field.get(source);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Optional<String> fromServletHeader() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return nonBlank(request.getHeader(CorrelationConstants.CORRELATION_HEADER));
    }

    private static Optional<String> nonBlank(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }
}
