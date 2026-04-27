package com.eum.rag.embedding.client;

import com.eum.rag.common.config.properties.RagAiProperties;
import com.eum.rag.common.exception.BusinessException;
import com.eum.rag.common.exception.ErrorCode;
import com.eum.rag.common.resilience.GeminiResilienceExecutor;
import com.eum.rag.common.resilience.NonRetryableGeminiException;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiEmbeddingClient implements EmbeddingClient {

    private final WebClient geminiWebClient;
    private final RagAiProperties ragAiProperties;
    private final MeterRegistry meterRegistry;
    private final GeminiResilienceExecutor geminiResilienceExecutor;

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        if (ragAiProperties.apiKey() == null || ragAiProperties.apiKey().isBlank()) {
            log.warn("GEMINI_API_KEY is not set. Falling back to deterministic local embedding for development.");
            return localFallbackEmbedding(text, ragAiProperties.embeddingDimension());
        }

        try {
            return geminiResilienceExecutor.execute(() -> requestEmbedding(text));
        } catch (NonRetryableGeminiException exception) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "Gemini API quota exceeded (429). Please try again later.");
        } catch (RuntimeException exception) {
            log.warn("Gemini embedding call failed after resilience policies: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR,
                    "Failed to call Gemini embedding API: " + exception.getMessage());
        }
    }

    private List<Float> requestEmbedding(String text) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean success = false;
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", ragAiProperties.embeddingModel());
            requestBody.put("input", text);

            JsonNode response = geminiWebClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(ragAiProperties.readTimeoutMs()))
                    .onErrorResume(WebClientResponseException.TooManyRequests.class,
                            error -> Mono.error(new NonRetryableGeminiException("Gemini quota exceeded", error)))
                    .onErrorResume(WebClientResponseException.class,
                            error -> Mono.error(new IllegalStateException("Gemini embedding API call failed: " + error.getStatusCode(), error)))
                    .onErrorResume(error -> Mono.error(new IllegalStateException("Gemini embedding API call failed", error)))
                    .block();

            if (response == null || response.path("data").isEmpty()) {
                throw new BusinessException(ErrorCode.EMBEDDING_ERROR, "Gemini embedding response is empty.");
            }

            JsonNode vectorNode = response.path("data").get(0).path("embedding");
            if (!vectorNode.isArray() || vectorNode.isEmpty()) {
                throw new BusinessException(ErrorCode.EMBEDDING_ERROR, "Gemini embedding vector is missing.");
            }

            List<Float> vector = new ArrayList<>(vectorNode.size());
            vectorNode.forEach(item -> vector.add((float) item.asDouble()));
            success = true;
            return vector;
        } finally {
            sample.stop(Timer.builder("rag.embedding.api.duration")
                    .tag("provider", "gemini")
                    .tag("result", success ? "success" : "failure")
                    .register(meterRegistry));
        }
    }

    private List<Float> localFallbackEmbedding(String text, int dimension) {
        long seed = text.hashCode();
        SplittableRandom random = new SplittableRandom(seed);
        List<Float> vector = new ArrayList<>(dimension);

        double norm = 0.0d;
        for (int i = 0; i < dimension; i++) {
            float value = (float) (random.nextDouble(-1.0, 1.0));
            vector.add(value);
            norm += value * value;
        }

        float scale = (float) (1.0 / Math.sqrt(Math.max(norm, 1e-12)));
        for (int i = 0; i < vector.size(); i++) {
            vector.set(i, vector.get(i) * scale);
        }

        return vector;
    }
}
