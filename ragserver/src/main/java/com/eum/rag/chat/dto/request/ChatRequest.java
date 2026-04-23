package com.eum.rag.chat.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 채팅 요청 DTO.
 * 첫 턴에서 sessionId를 비워도 되며, 서버가 생성해 응답으로 반환한다.
 */
public record ChatRequest(
        String sessionId,
        @NotBlank String question
) {
}
