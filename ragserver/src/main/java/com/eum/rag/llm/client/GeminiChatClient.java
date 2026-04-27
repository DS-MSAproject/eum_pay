package com.eum.rag.llm.client;

import com.eum.rag.common.config.properties.RagAiProperties;
import com.eum.rag.common.exception.BusinessException;
import com.eum.rag.common.exception.ErrorCode;
import com.eum.rag.common.resilience.GeminiResilienceExecutor;
import com.eum.rag.common.resilience.NonRetryableGeminiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiChatClient implements LlmClient {

    private final WebClient geminiWebClient;
    private final RagAiProperties ragAiProperties;
    private final GeminiResilienceExecutor geminiResilienceExecutor;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (ragAiProperties.apiKey() == null || ragAiProperties.apiKey().isBlank()) {
            return fallbackFromContext(userPrompt);
        }

        try {
            return geminiResilienceExecutor.execute(() -> request(systemPrompt, userPrompt));
        } catch (NonRetryableGeminiException exception) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "Gemini API quota exceeded (429). Please try again later.");
        } catch (RuntimeException exception) {
            log.warn("Gemini chat call failed after resilience policies: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.LLM_GENERATION_ERROR,
                    "Failed to generate answer: " + exception.getMessage());
        }
    }

    private String request(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", ragAiProperties.chatModel());
        body.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        });
        body.put("temperature", 0);

        JsonNode response = geminiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(ragAiProperties.readTimeoutMs()))
                .onErrorResume(WebClientResponseException.TooManyRequests.class,
                        error -> Mono.error(new NonRetryableGeminiException("Gemini quota exceeded", error)))
                .onErrorResume(WebClientResponseException.class,
                        error -> Mono.error(new IllegalStateException("Gemini chat API call failed: " + error.getStatusCode(), error)))
                .block();

        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new BusinessException(ErrorCode.LLM_GENERATION_ERROR, "LLM response content is empty.");
        }
        return content.asText();
    }

    private String fallbackFromContext(String userPrompt) {
        if (userPrompt.contains("[HISTORY]") && userPrompt.contains("[QUESTION]")) {
            return extractQuestion(userPrompt);
        }

        String marker = "[CONTEXT]";
        int idx = userPrompt.indexOf(marker);
        if (idx < 0 || userPrompt.isBlank()) {
            return "제공된 문맥에서 확인되지 않습니다.";
        }

        String contextBlock = userPrompt.substring(idx);
        if (!contextBlock.contains("[SOURCE")) {
            return "제공된 문맥에서 확인되지 않습니다.";
        }

        return "제공된 문맥에서 확인된 내용만 안내할 수 있습니다. 현재 개발 모드에서는 생성형 응답이 제한됩니다.";
    }

    private String extractQuestion(String userPrompt) {
        String marker = "[QUESTION]";
        int index = userPrompt.indexOf(marker);
        if (index < 0) {
            return "";
        }
        return userPrompt.substring(index + marker.length()).trim();
    }
}
