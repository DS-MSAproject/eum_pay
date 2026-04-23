package com.eum.searchserver.controller;

import com.eum.searchserver.global.config.SearchCategoryProperties;
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
@RequestMapping("/search/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final SearchCategoryProperties searchCategoryProperties;
    private final Environment environment;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${dseum.search.categories.config-endpoint:http://configserver:8071/dseum-search/dev}")
    private String configEndpoint;

    @GetMapping
    public Mono<Map<String, Object>> getCategories() {
        List<Map<String, Object>> fromEnvironment = resolveCategoriesFromEnvironment();
        if (!fromEnvironment.isEmpty()) {
            return Mono.just(success(fromEnvironment));
        }

        List<Map<String, Object>> fromProperties = resolveCategoriesFromProperties();
        if (!fromProperties.isEmpty()) {
            return Mono.just(success(fromProperties));
        }

        return fetchCategoriesFromConfigServer()
                .map(this::success)
                .defaultIfEmpty(success(List.of()));
    }

    private Mono<List<Map<String, Object>>> fetchCategoriesFromConfigServer() {
        return webClientBuilder.build()
                .get()
                .uri(configEndpoint)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseCategoriesFromConfigResponse)
                .onErrorReturn(List.of());
    }

    private List<Map<String, Object>> parseCategoriesFromConfigResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode propertySources = root.path("propertySources");
            if (!propertySources.isArray()) {
                return List.of();
            }

            for (JsonNode sourceNode : propertySources) {
                JsonNode source = sourceNode.path("source");
                if (source.isMissingNode() || !source.isObject()) {
                    continue;
                }
                List<Map<String, Object>> categories = extractCategoriesFromFlatSource(source);
                if (!categories.isEmpty()) {
                    return categories;
                }
            }
        } catch (Exception ignore) {
            return List.of();
        }

        return List.of();
    }

    private List<Map<String, Object>> extractCategoriesFromFlatSource(JsonNode source) {
        List<Map<String, Object>> categories = new ArrayList<>();

        for (int categoryIndex = 0; ; categoryIndex++) {
            String base = "dseum.search.categories[" + categoryIndex + "]";
            JsonNode idNode = source.get(base + ".id");
            JsonNode labelNode = source.get(base + ".label");

            String id = idNode != null && !idNode.isNull() ? idNode.asText() : null;
            String label = labelNode != null && !labelNode.isNull() ? labelNode.asText() : null;

            if (!hasText(id) && !hasText(label)) {
                break;
            }

            List<Map<String, Object>> subCategories = new ArrayList<>();
            for (int subIndex = 0; ; subIndex++) {
                String subBase = base + ".subCategories[" + subIndex + "]";
                JsonNode subCodeNode = source.get(subBase + ".code");
                JsonNode subLabelNode = source.get(subBase + ".label");
                JsonNode subIdNode = source.get(subBase + ".id");

                String subCode = subCodeNode != null && !subCodeNode.isNull() ? subCodeNode.asText() : null;
                String subLabel = subLabelNode != null && !subLabelNode.isNull() ? subLabelNode.asText() : null;
                Long subId = (subIdNode != null && subIdNode.isNumber()) ? subIdNode.asLong() : null;

                if (!hasText(subCode) && !hasText(subLabel) && subId == null) {
                    break;
                }

                Map<String, Object> subCategory = new LinkedHashMap<>();
                if (subId != null) subCategory.put("id", subId);
                if (hasText(subCode)) subCategory.put("code", subCode);
                if (hasText(subLabel)) subCategory.put("label", subLabel);
                subCategories.add(subCategory);
            }

            Map<String, Object> category = new LinkedHashMap<>();
            if (hasText(id)) category.put("id", id);
            if (hasText(label)) category.put("label", label);
            category.put("subCategories", subCategories);
            categories.add(category);
        }

        return categories;
    }

    private List<Map<String, Object>> resolveCategoriesFromEnvironment() {
        List<Map<String, Object>> categories = new ArrayList<>();

        for (int categoryIndex = 0; ; categoryIndex++) {
            String base = "dseum.search.categories[" + categoryIndex + "]";
            String id = environment.getProperty(base + ".id");
            String label = environment.getProperty(base + ".label");

            if (!hasText(id) && !hasText(label)) {
                break;
            }

            List<Map<String, Object>> subCategories = new ArrayList<>();
            for (int subIndex = 0; ; subIndex++) {
                String subBase = base + ".subCategories[" + subIndex + "]";
                String subCode = environment.getProperty(subBase + ".code");
                String subLabel = environment.getProperty(subBase + ".label");
                Long subId = environment.getProperty(subBase + ".id", Long.class);

                if (!hasText(subCode) && !hasText(subLabel) && subId == null) {
                    break;
                }

                Map<String, Object> subCategory = new LinkedHashMap<>();
                if (subId != null) subCategory.put("id", subId);
                if (hasText(subCode)) subCategory.put("code", subCode);
                if (hasText(subLabel)) subCategory.put("label", subLabel);
                subCategories.add(subCategory);
            }

            Map<String, Object> category = new LinkedHashMap<>();
            if (hasText(id)) category.put("id", id);
            if (hasText(label)) category.put("label", label);
            category.put("subCategories", subCategories);
            categories.add(category);
        }

        return categories;
    }

    private List<Map<String, Object>> resolveCategoriesFromProperties() {
        return searchCategoryProperties.getCategories().stream()
                .map(category -> {
                    Map<String, Object> categoryMap = new LinkedHashMap<>();
                    categoryMap.put("id", category.getId());
                    categoryMap.put("label", category.getLabel());
                    List<Map<String, Object>> subCategories = category.getSubCategories().stream()
                            .map(subCategory -> {
                                Map<String, Object> subMap = new LinkedHashMap<>();
                                subMap.put("id", subCategory.getId());
                                subMap.put("code", subCategory.getCode());
                                subMap.put("label", subCategory.getLabel());
                                return subMap;
                            })
                            .toList();
                    categoryMap.put("subCategories", subCategories);
                    return categoryMap;
                })
                .toList();
    }

    private Map<String, Object> success(List<Map<String, Object>> categories) {
        return Map.of(
                "status", "success",
                "data", categories
        );
    }
}
