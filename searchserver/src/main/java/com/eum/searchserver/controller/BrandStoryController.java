package com.eum.searchserver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

@RestController
@RequestMapping("/search/brand-story")
@RequiredArgsConstructor
public class BrandStoryController {
    private static final String BRAND_STORY_DETAIL_URL = "/search/brand-story/detail";

    private final Environment environment;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${dseum.search.brand-story.config-endpoint:http://configserver:8071/dseum-search/dev}")
    private String configEndpoint;

    @GetMapping
    public Mono<Map<String, Object>> getBrandStory() {
        Map<String, Object> fromEnvironment = resolveFromEnvironment();
        if (!fromEnvironment.isEmpty()) {
            return Mono.just(successMainCard(extractMainCardOnly(fromEnvironment)));
        }

        return fetchFromConfigServer()
                .map(this::extractMainCardOnly)
                .map(this::successMainCard)
                .defaultIfEmpty(successMainCard(Map.of()));
    }

    @GetMapping("/detail")
    public Mono<Map<String, Object>> getBrandStoryDetail() {
        Map<String, Object> fromEnvironment = resolveFromEnvironment();
        if (!fromEnvironment.isEmpty()) {
            return Mono.just(successBrandPage(extractBrandPageOnly(fromEnvironment)));
        }

        return fetchFromConfigServer()
                .map(this::extractBrandPageOnly)
                .map(this::successBrandPage)
                .defaultIfEmpty(successBrandPage(List.of()));
    }

    private Mono<Map<String, Object>> fetchFromConfigServer() {
        return webClientBuilder.build()
                .get()
                .uri(configEndpoint)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseFromConfigResponse)
                .onErrorReturn(defaultData());
    }

    private Map<String, Object> parseFromConfigResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode propertySources = root.path("propertySources");
            if (!propertySources.isArray()) {
                return defaultData();
            }

            for (JsonNode sourceNode : propertySources) {
                JsonNode source = sourceNode.path("source");
                if (!source.isObject()) {
                    continue;
                }
                Map<String, Object> parsed = extractFromFlatSource(source);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        } catch (Exception ignore) {
            return defaultData();
        }
        return defaultData();
    }

    private Map<String, Object> extractFromFlatSource(JsonNode source) {
        Map<String, Object> mainCard = extractMainCardFromSource(source, "dseum.search.brandStory.mainCard.");
        List<Map<String, Object>> brandPage = extractBrandPageFromSource(source, "dseum.search.brandStory.brandPage");

        if (mainCard.isEmpty() && brandPage.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mainCard", mainCard);
        data.put("brandPage", brandPage);
        return data;
    }

    private Map<String, Object> resolveFromEnvironment() {
        Map<String, Object> mainCard = extractMainCardFromEnvironment("dseum.search.brandStory.mainCard.");
        List<Map<String, Object>> brandPage = extractBrandPageFromEnvironment("dseum.search.brandStory.brandPage");

        if (mainCard.isEmpty() && brandPage.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mainCard", mainCard);
        data.put("brandPage", brandPage);
        return data;
    }

    private Map<String, Object> extractMainCardFromEnvironment(String prefix) {
        String imageUrl = environment.getProperty(prefix + "imageUrl");
        String buttonText = environment.getProperty(prefix + "buttonText");
        String buttonUrl = environment.getProperty(prefix + "buttonUrl");

        Map<String, Object> mainCard = new LinkedHashMap<>();
        if (hasText(imageUrl)) mainCard.put("imageUrl", imageUrl);
        if (hasText(buttonText)) mainCard.put("buttonText", buttonText);
        if (hasText(buttonUrl)) mainCard.put("buttonUrl", buttonUrl);
        return mainCard;
    }

    private List<Map<String, Object>> extractBrandPageFromEnvironment(String prefix) {
        List<Map<String, Object>> brandPage = new ArrayList<>();
        for (int index = 0; ; index++) {
            String itemPrefix = prefix + "[" + index + "].";
            String imageUrl = environment.getProperty(itemPrefix + "imageUrl");
            String buttonText = environment.getProperty(itemPrefix + "buttonText");
            String buttonUrl = environment.getProperty(itemPrefix + "buttonUrl");
            Integer displayOrder = environment.getProperty(itemPrefix + "displayOrder", Integer.class);

            if (!hasText(imageUrl) && !hasText(buttonText) && !hasText(buttonUrl)
                    && displayOrder == null) {
                break;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            if (hasText(imageUrl)) item.put("imageUrl", imageUrl);
            if (hasText(buttonText)) item.put("buttonText", buttonText);
            if (hasText(buttonUrl)) item.put("buttonUrl", buttonUrl);
            if (displayOrder != null) item.put("displayOrder", displayOrder);
            brandPage.add(item);
        }
        return brandPage;
    }

    private Map<String, Object> extractMainCardFromSource(JsonNode source, String prefix) {
        JsonNode imageUrlNode = source.get(prefix + "imageUrl");
        JsonNode buttonTextNode = source.get(prefix + "buttonText");
        JsonNode buttonUrlNode = source.get(prefix + "buttonUrl");

        String imageUrl = imageUrlNode != null && !imageUrlNode.isNull() ? imageUrlNode.asText() : null;
        String buttonText = buttonTextNode != null && !buttonTextNode.isNull() ? buttonTextNode.asText() : null;
        String buttonUrl = buttonUrlNode != null && !buttonUrlNode.isNull() ? buttonUrlNode.asText() : null;

        Map<String, Object> mainCard = new LinkedHashMap<>();
        if (hasText(imageUrl)) mainCard.put("imageUrl", imageUrl);
        if (hasText(buttonText)) mainCard.put("buttonText", buttonText);
        if (hasText(buttonUrl)) mainCard.put("buttonUrl", buttonUrl);
        return mainCard;
    }

    private List<Map<String, Object>> extractBrandPageFromSource(JsonNode source, String prefix) {
        List<Map<String, Object>> brandPage = new ArrayList<>();
        for (int index = 0; ; index++) {
            String itemPrefix = prefix + "[" + index + "].";
            JsonNode imageUrlNode = source.get(itemPrefix + "imageUrl");
            JsonNode buttonTextNode = source.get(itemPrefix + "buttonText");
            JsonNode buttonUrlNode = source.get(itemPrefix + "buttonUrl");
            JsonNode displayOrderNode = source.get(itemPrefix + "displayOrder");

            String imageUrl = imageUrlNode != null && !imageUrlNode.isNull() ? imageUrlNode.asText() : null;
            String buttonText = buttonTextNode != null && !buttonTextNode.isNull() ? buttonTextNode.asText() : null;
            String buttonUrl = buttonUrlNode != null && !buttonUrlNode.isNull() ? buttonUrlNode.asText() : null;
            Integer displayOrder = (displayOrderNode != null && displayOrderNode.isNumber()) ? displayOrderNode.asInt() : null;

            if (!hasText(imageUrl) && !hasText(buttonText) && !hasText(buttonUrl)
                    && displayOrder == null) {
                break;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            if (hasText(imageUrl)) item.put("imageUrl", imageUrl);
            if (hasText(buttonText)) item.put("buttonText", buttonText);
            if (hasText(buttonUrl)) item.put("buttonUrl", buttonUrl);
            if (displayOrder != null) item.put("displayOrder", displayOrder);
            brandPage.add(item);
        }
        return brandPage;
    }

    private Map<String, Object> defaultData() {
        Map<String, Object> defaultData = new LinkedHashMap<>();
        defaultData.put("mainCard", Map.of());
        defaultData.put("brandPage", List.of());
        return defaultData;
    }

    private Map<String, Object> success(Map<String, Object> data) {
        return Map.of(
                "status", "success",
                "data", data
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractBrandPageOnly(Map<String, Object> data) {
        Object brandPage = data.get("brandPage");
        if (!(brandPage instanceof List<?> rawList)) {
            return List.of();
        }

        return rawList.stream()
                .filter(Map.class::isInstance)
                .map(item -> sanitizeBrandPageItem((Map<String, Object>) item))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMainCardOnly(Map<String, Object> data) {
        Object mainCard = data.get("mainCard");
        if (!(mainCard instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        Object imageUrl = rawMap.get("imageUrl");
        Object buttonText = rawMap.get("buttonText");
        if (imageUrl instanceof String s && hasText(s)) {
            normalized.put("imageUrl", s);
        }
        if (buttonText instanceof String s && hasText(s)) {
            normalized.put("buttonText", s);
        }
        normalized.put("buttonUrl", BRAND_STORY_DETAIL_URL);
        return normalized;
    }

    private Map<String, Object> sanitizeBrandPageItem(Map<String, Object> raw) {
        Map<String, Object> item = new LinkedHashMap<>();
        Object imageUrl = raw.get("imageUrl");
        Object displayOrder = raw.get("displayOrder");
        if (imageUrl instanceof String s && hasText(s)) {
            item.put("imageUrl", s);
        }
        if (displayOrder instanceof Number n) {
            item.put("displayOrder", n.intValue());
        }
        return item;
    }

    private Map<String, Object> successBrandPage(List<Map<String, Object>> brandPage) {
        return Map.of(
                "status", "success",
                "data", brandPage
        );
    }

    private Map<String, Object> successMainCard(Map<String, Object> mainCard) {
        return Map.of(
                "status", "success",
                "data", Map.of("mainCard", mainCard)
        );
    }
}
