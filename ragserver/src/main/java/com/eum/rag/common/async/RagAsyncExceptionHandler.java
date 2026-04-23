package com.eum.rag.common.async;

import com.eum.rag.document.domain.DocumentStatus;
import com.eum.rag.document.event.DocumentIngestionRequestedEvent;
import com.eum.rag.document.repository.DocumentRepository;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final DocumentRepository documentRepository;

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("Async uncaught exception in method={}", method.getName(), ex);

        Arrays.stream(params)
                .filter(DocumentIngestionRequestedEvent.class::isInstance)
                .map(DocumentIngestionRequestedEvent.class::cast)
                .findFirst()
                .ifPresent(event -> documentRepository.updateStatus(
                        event.documentId(),
                        DocumentStatus.FAILED,
                        shorten(ex.getMessage()),
                        OffsetDateTime.now()
                ));
    }

    private String shorten(String message) {
        if (message == null) {
            return "Async pipeline failed.";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
