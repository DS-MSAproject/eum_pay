package com.eum.rag.chat.service;

import com.eum.rag.chat.dto.response.ChatResponse;
import com.eum.rag.common.config.properties.RagCommerceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommerceAnswerService {

    private static final List<String> GENERIC_RECOMMENDATION_TOKENS = List.of(
            "상품", "간식", "사료", "추천", "해줘", "해주세요", "알려줘", "알려주세요"
    );

    private final RagCommerceProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<HandledCommerceAnswer> tryHandle(String question) {
        if (!StringUtils.hasText(question)) {
            return Optional.empty();
        }

        String normalized = question.trim();
        if (isRecommendationQuestion(normalized)) {
            return Optional.of(handleRecommendation(normalized));
        }
        if (isProductInfoQuestion(normalized)) {
            return Optional.of(handleProductInfo(normalized));
        }
        return Optional.empty();
    }

    private HandledCommerceAnswer handleRecommendation(String question) {
        String keyword = extractRecommendationKeyword(question);
        List<ProductItem> products = StringUtils.hasText(keyword)
                ? searchProducts(keyword, Math.max(3, properties.bestsellerSize()))
                : fetchBestsellerProducts(Math.max(3, properties.bestsellerSize()));

        if (products.isEmpty()) {
            return new HandledCommerceAnswer(
                    "추천할 상품 정보를 찾지 못했습니다. 잠시 후 다시 시도해주세요.",
                    List.of()
            );
        }

        List<ProductItem> top3 = products.stream().limit(3).toList();
        String names = top3.stream().map(ProductItem::title).toList().toString()
                .replace("[", "")
                .replace("]", "");
        String answer = "추천하는 상품으로는 " + names + "이 있습니다.";
        return new HandledCommerceAnswer(answer, toSourceItems(top3, "searchserver-products"));
    }

    private HandledCommerceAnswer handleProductInfo(String question) {
        String keyword = cleanupQuestionKeyword(question);
        log.info("commerce product-info keyword='{}'", keyword);
        List<ProductItem> products = searchProducts(keyword, 3);
        log.info("commerce product-info primary hit={}", products.size());
        if (products.isEmpty()) {
            products = searchProducts(keyword.replace(" ", ""), 3);
            log.info("commerce product-info compact hit={}", products.size());
        }
        if (products.isEmpty()) {
            String fallback = fallbackKeyword(keyword);
            if (StringUtils.hasText(fallback)) {
                products = searchProducts(fallback, 3);
            }
        }
        if (products.isEmpty()) {
            for (String token : keyword.split("\\s+")) {
                if (!StringUtils.hasText(token) || token.length() < 2) {
                    continue;
                }
                products = searchProducts(token, 3);
                if (!products.isEmpty()) {
                    break;
                }
            }
        }
        if (products.isEmpty()) {
            return new HandledCommerceAnswer(
                    "요청하신 상품 정보를 찾지 못했습니다. 상품명이나 키워드를 조금 더 구체적으로 알려주세요.",
                    List.of()
            );
        }

        ProductItem first = products.get(0);
        String info = StringUtils.hasText(first.productInfo()) ? first.productInfo() : first.content();
        if (!StringUtils.hasText(info)) {
            info = "해당 상품의 상세 설명(productInfo)은 아직 등록되지 않았습니다.";
        }

        String answer = first.title() + " 상품 정보입니다. " + info;
        return new HandledCommerceAnswer(answer, toSourceItems(products, "searchserver-products"));
    }

    private List<ProductItem> fetchBestsellerProducts(int size) {
        try {
            String url = properties.searchBaseUrl() + "/search/products/bestseller?page=0&size=" + size;
            JsonNode root = getJson(url);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<ProductItem> items = new ArrayList<>();
            for (JsonNode node : data) {
                String title = text(node, "productTitle");
                Long id = longValue(node, "id");
                if (StringUtils.hasText(title)) {
                    items.add(new ProductItem(id, title, null, null));
                }
            }
            return items;
        } catch (Exception e) {
            log.warn("failed to fetch bestseller products: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ProductItem> searchProducts(String keyword, int size) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.searchBaseUrl())
                    .path("/search/products")
                    .queryParam("keyword", keyword)
                    .queryParam("page", 0)
                    .toUriString();
            JsonNode root = getJson(url);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<ProductItem> items = new ArrayList<>();
            for (JsonNode node : data) {
                String title = firstNonEmpty(text(node, "productTitle"), text(node, "title"));
                Long id = longValue(node, "id");
                String productInfo = firstNonEmpty(text(node, "productInfo"), text(node, "product_info"));
                String content = text(node, "content");
                if (StringUtils.hasText(title)) {
                    items.add(new ProductItem(id, title, productInfo, content));
                }
                if (items.size() >= size) {
                    break;
                }
            }
            return items;
        } catch (Exception e) {
            log.warn("failed to search products with keyword='{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private JsonNode getJson(String url) throws Exception {
        RestClient client = RestClient.builder().baseUrl("").build();
        String body = client.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private boolean isRecommendationQuestion(String question) {
        return question.contains("추천");
    }

    private boolean isProductInfoQuestion(String question) {
        return question.contains("상품")
                && (question.contains("정보") || question.contains("설명") || question.contains("알려"));
    }

    private String extractRecommendationKeyword(String question) {
        String cleaned = cleanupQuestionKeyword(question);
        String lower = cleaned.toLowerCase(Locale.ROOT);
        for (String token : GENERIC_RECOMMENDATION_TOKENS) {
            lower = lower.replace(token, "");
        }
        lower = lower.replace("해줄래", "").replace("추천해줘", "").trim();
        return lower;
    }

    private String cleanupQuestionKeyword(String question) {
        String cleaned = question
                .replace("추천해줘", "")
                .replace("추천해 주세요", "")
                .replace("추천해주세요", "")
                .replace("알려줘", "")
                .replace("알려주세요", "")
                .replace("상품", "")
                .replace("정보", "")
                .replace("설명", "")
                .replace("뭐야", "")
                .replace("?", "")
                .trim();
        return StringUtils.hasText(cleaned) ? cleaned : question.trim();
    }

    private String fallbackKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String[] parts = keyword.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return parts[0];
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isNumber() ? value.asLong() : null;
    }

    private String firstNonEmpty(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return b;
    }

    private List<ChatResponse.SourceItem> toSourceItems(List<ProductItem> products, String filename) {
        List<ChatResponse.SourceItem> sourceItems = new ArrayList<>();
        for (ProductItem product : products) {
            sourceItems.add(new ChatResponse.SourceItem(
                    product.id() == null ? "unknown" : String.valueOf(product.id()),
                    filename,
                    "product-" + (product.id() == null ? "unknown" : product.id()),
                    product.title(),
                    1.0
            ));
        }
        return sourceItems;
    }

    private record ProductItem(
            Long id,
            String title,
            String productInfo,
            String content
    ) {
    }

    public record HandledCommerceAnswer(
            String answer,
            List<ChatResponse.SourceItem> sources
    ) {
    }
}
