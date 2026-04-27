package com.eum.rag.chat.dto.response;

import java.util.List;

/**
 * 채팅 UI 렌더링용 응답 DTO.
 */
public record ChatResponse(
        String sessionId,
        String rewrittenQuestion,
        String answer,
        List<SourceItem> sources
) {
    // snippet은 채팅 UI 근거 패널에 빠르게 표시하기 위해 축약된 텍스트다.
    public record SourceItem(
            String documentId,
            String filename,
            String chunkId,
            String snippet,
            double score
    ) {
    }
}
