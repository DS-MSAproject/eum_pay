package com.eum.rag.document.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentReindexLockService {

    private static final String PREFIX = "rag:reindex:lock:";

    private final StringRedisTemplate stringRedisTemplate;

    public String acquire(String documentId, Duration waitTimeout, Duration lockTtl) {
        String key = key(documentId);
        String token = UUID.randomUUID().toString();
        long deadline = Instant.now().plus(waitTimeout).toEpochMilli();

        while (Instant.now().toEpochMilli() < deadline) {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
            if (Boolean.TRUE.equals(locked)) {
                return token;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return null;
    }

    public void release(String documentId, String token) {
        if (token == null) {
            return;
        }

        String key = key(documentId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (token.equals(current)) {
            stringRedisTemplate.delete(key);
        }
    }

    private String key(String documentId) {
        return PREFIX + documentId;
    }
}
