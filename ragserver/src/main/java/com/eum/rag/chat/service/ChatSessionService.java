package com.eum.rag.chat.service;

import com.eum.rag.chat.domain.ChatMessage;
import com.eum.rag.chat.domain.ChatRole;
import com.eum.rag.chat.domain.ChatSession;
import com.eum.rag.chat.dto.response.SessionHistoryResponse;
import com.eum.rag.chat.repository.ChatSessionRepository;
import com.eum.rag.common.config.properties.RagSessionProperties;
import com.eum.rag.common.exception.BusinessException;
import com.eum.rag.common.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final RagSessionProperties ragSessionProperties;

    public ChatSession loadOrCreate(String requestedSessionId) {
        if (requestedSessionId == null || requestedSessionId.isBlank()) {
            String generated = UUID.randomUUID().toString();
            ChatSession session = new ChatSession(generated, List.of(), Instant.now().toEpochMilli());
            persist(session);
            return session;
        }

        return chatSessionRepository.findById(requestedSessionId, ttlSeconds())
                .orElseGet(() -> {
                    ChatSession created = new ChatSession(requestedSessionId, List.of(), Instant.now().toEpochMilli());
                    persist(created);
                    return created;
                });
    }

    public SessionHistoryResponse getHistory(String sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId, ttlSeconds())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        List<SessionHistoryResponse.SessionMessageItem> items = session.messages().stream()
                .map(message -> new SessionHistoryResponse.SessionMessageItem(
                        message.role().name().toLowerCase(),
                        message.content(),
                        message.timestamp()
                ))
                .toList();

        return new SessionHistoryResponse(session.sessionId(), items.size(), items);
    }

    public ChatSession appendTurn(ChatSession session, String userQuestion, String assistantAnswer) {
        List<ChatMessage> merged = new ArrayList<>(session.messages());
        long now = Instant.now().toEpochMilli();
        merged.add(new ChatMessage(ChatRole.USER, userQuestion, now));
        merged.add(new ChatMessage(ChatRole.ASSISTANT, assistantAnswer, now));

        int maxMessages = ragSessionProperties.maxMessages();
        if (merged.size() > maxMessages) {
            merged = merged.subList(merged.size() - maxMessages, merged.size());
        }

        ChatSession updated = new ChatSession(session.sessionId(), merged, now);
        persist(updated);
        return updated;
    }

    private void persist(ChatSession session) {
        chatSessionRepository.save(session, ttlSeconds());
    }

    private long ttlSeconds() {
        return ragSessionProperties.ttlHours() * 3600L;
    }
}
