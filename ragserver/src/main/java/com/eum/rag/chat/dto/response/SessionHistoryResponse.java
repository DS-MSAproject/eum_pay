package com.eum.rag.chat.dto.response;

import java.util.List;

/**
 * 채팅 UI에서 이전 대화를 복원하기 위한 세션 이력 응답 DTO.
 */
public record SessionHistoryResponse(
        String sessionId,
        int messageCount,
        List<SessionMessageItem> messages
) {
    public record SessionMessageItem(
            String role,
            String content,
            long timestamp
    ) {
    }
}
