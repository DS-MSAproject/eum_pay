package com.eum.rag.chat.service;

import com.eum.rag.chat.domain.ChatMessage;
import com.eum.rag.common.config.properties.RagChatProperties;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

    private static final Set<String> CONTEXTUAL_TOKENS = Set.of("그럼", "그건", "이건", "그거", "이거", "그때", "그럼요");

    private final RagChatProperties ragChatProperties;

    public QueryRewriteService(RagChatProperties ragChatProperties) {
        this.ragChatProperties = ragChatProperties;
    }

    public boolean shouldRewrite(String question, List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }

        if (question == null || question.isBlank()) {
            return false;
        }

        if (question.length() <= ragChatProperties.rewriteShortQuestionLength()) {
            return true;
        }

        String normalized = question.trim();
        return CONTEXTUAL_TOKENS.stream().anyMatch(normalized::contains);
    }
}
