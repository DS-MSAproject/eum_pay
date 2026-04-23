package com.eum.searchserver.dto.response;

import java.util.Map;

public record NavigationApiMeta(
        String method,
        String endpoint,
        Map<String, Object> query,
        String responsePath
) {
}
