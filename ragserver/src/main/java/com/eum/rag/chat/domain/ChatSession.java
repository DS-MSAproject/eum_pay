package com.eum.rag.chat.domain;

import java.util.ArrayList;
import java.util.List;

public record ChatSession(
        String sessionId,
        List<ChatMessage> messages,
        long updatedAt
) {
    public ChatSession {
        messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }
}
