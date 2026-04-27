package com.eum.productserver.dto.response;

import com.eum.productserver.entity.Product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProductTagResolver {

    private static final String NEW_PRODUCT_TAG = "신상품";
    private static final int NEW_PRODUCT_DAYS = 7;

    private ProductTagResolver() {
    }

    public static List<String> resolve(Product product) {
        if (product == null) {
            return List.of();
        }

        Set<String> tags = parseTags(product.getTags());
        if (isNewProduct(product)) {
            tags.add(NEW_PRODUCT_TAG);
        }

        if (tags.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(tags);
    }

    public static String resolveCsv(Product product) {
        List<String> tags = resolve(product);
        if (tags.isEmpty()) {
            return null;
        }
        return String.join(",", tags);
    }

    public static List<String> fromRawTags(String rawTags) {
        Set<String> tags = parseTags(rawTags);
        if (tags.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(tags);
    }

    private static Set<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return new LinkedHashSet<>();
        }

        return Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isNewProduct(Product product) {
        LocalDateTime createdDate = product.getCreatedDate();
        if (createdDate == null) {
            return false;
        }
        return !createdDate.isBefore(LocalDateTime.now().minusDays(NEW_PRODUCT_DAYS));
    }
}
