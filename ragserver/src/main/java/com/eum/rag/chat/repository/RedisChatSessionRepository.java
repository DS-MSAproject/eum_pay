package com.eum.rag.chat.repository;

import com.eum.rag.chat.domain.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisChatSessionRepository implements ChatSessionRepository {

    private static final String PREFIX = "rag:session:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(ChatSession session, long ttlSeconds) {
        redisTemplate.opsForValue().set(key(session.sessionId()), session, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<ChatSession> findById(String sessionId, long ttlSeconds) {
        Object raw = redisTemplate.opsForValue().get(key(sessionId));
        if (raw == null) {
            return Optional.empty();
        }

        redisTemplate.expire(key(sessionId), Duration.ofSeconds(ttlSeconds));

        if (raw instanceof ChatSession chatSession) {
            return Optional.of(chatSession);
        }
        if (raw instanceof Map<?, ?> map) {
            return Optional.of(objectMapper.convertValue(map, ChatSession.class));
        }
        return Optional.empty();
    }

    private String key(String sessionId) {
        return PREFIX + sessionId;
    }
}
