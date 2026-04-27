package com.eum.common.correlation;

import java.lang.annotation.Annotation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Aspect
public class CorrelationAspect {

    private static final Logger log = LoggerFactory.getLogger(CorrelationAspect.class);

    @Around("@annotation(com.eum.common.correlation.Correlated) || @within(com.eum.common.correlation.Correlated)")
    public Object bindCorrelationContext(ProceedingJoinPoint joinPoint) throws Throwable {
        String previous = CorrelationContext.get();
        boolean overwrite = previous == null || previous.isBlank();
        String activeCorrelationId = previous;
        String methodName = methodName(joinPoint);
        long startedAtNanos = System.nanoTime();

        if (overwrite) {
            String candidate = extractCorrelationFromArguments(joinPoint);
            String resolved = CorrelationIdResolver.resolveOrGenerate(candidate);
            CorrelationContext.set(resolved);
            MDC.put("correlationId", resolved);
            activeCorrelationId = resolved;
            log.info("[CORR][START] method={}, correlationId={}", methodName, activeCorrelationId);
        }

        if (activeCorrelationId == null || activeCorrelationId.isBlank()) {
            activeCorrelationId = CorrelationIdResolver.resolveOrGenerate(null);
        }

        try {
            Object result = joinPoint.proceed();
            if (overwrite) {
                long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                log.info("[CORR][END] method={}, correlationId={}, elapsedMs={}",
                        methodName, activeCorrelationId, elapsedMs);
            }
            return result;
        } catch (Throwable ex) {
            if (overwrite) {
                long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                log.warn("[CORR][ERROR] method={}, correlationId={}, elapsedMs={}, error={}",
                        methodName, activeCorrelationId, elapsedMs, ex.toString(), ex);
            }
            throw ex;
        } finally {
            if (overwrite) {
                CorrelationContext.clear();
                MDC.remove("correlationId");
            }
        }
    }

    private String extractCorrelationFromArguments(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation.annotationType() == CorrelationIdSource.class) {
                    return CorrelationIdResolver.fromSourceObject(args[i]);
                }
            }
        }
        return null;
    }

    private String methodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
}
