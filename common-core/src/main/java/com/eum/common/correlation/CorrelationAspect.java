package com.eum.common.correlation;

import java.lang.annotation.Annotation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;

@Aspect
public class CorrelationAspect {

    @Around("@annotation(com.eum.common.correlation.Correlated) || @within(com.eum.common.correlation.Correlated)")
    public Object bindCorrelationContext(ProceedingJoinPoint joinPoint) throws Throwable {
        String previous = CorrelationContext.get();
        boolean overwrite = previous == null || previous.isBlank();

        if (overwrite) {
            String candidate = extractCorrelationFromArguments(joinPoint);
            String resolved = CorrelationIdResolver.resolveOrGenerate(candidate);
            CorrelationContext.set(resolved);
            MDC.put("correlationId", resolved);
        }

        try {
            return joinPoint.proceed();
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
}
