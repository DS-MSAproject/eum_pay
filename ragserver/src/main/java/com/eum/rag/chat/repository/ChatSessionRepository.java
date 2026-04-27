package com.eum.rag.chat.repository;

import com.eum.rag.chat.domain.ChatSession;
import java.util.Optional;

public interface ChatSessionRepository {
    void save(ChatSession session, long ttlSeconds);

    Optional<ChatSession> findById(String sessionId, long ttlSeconds);
}
