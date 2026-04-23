package com.eum.rag.llm.service;

import com.eum.rag.chat.domain.ChatMessage;
import com.eum.rag.retrieval.dto.response.HybridSearchResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilderService {

    public String buildRewriteUserPrompt(String template, List<ChatMessage> history, String question) {
        String historyText = history.stream()
                .map(message -> message.role().name() + ": " + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(empty)");

        return template
                .replace("{{history}}", historyText)
                .replace("{{question}}", question);
    }

    public String buildAnswerUserPrompt(List<HybridSearchResponse.RetrievedChunk> chunks, String question) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            HybridSearchResponse.RetrievedChunk chunk = chunks.get(i);
            context.append("[SOURCE ").append(i + 1).append("]")
                    .append(" document_id=").append(chunk.documentId())
                    .append(", filename=").append(chunk.filename())
                    .append(", chunk_id=").append(chunk.chunkId())
                    .append("\n")
                    .append(chunk.text())
                    .append("\n\n");
        }

        return "[CONTEXT]\n" + context +
                "[QUESTION]\n" + question + "\n" +
                "[INSTRUCTION]\n문맥 근거만으로 한국어로 답변하세요.";
    }
}
