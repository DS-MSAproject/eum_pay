package com.eum.rag.chat.domain;

public record ChatMessage(
        ChatRole role,
        String content,
        long timestamp
) {
}
