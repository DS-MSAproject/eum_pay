package com.eum.searchserver.dto.response;

public record NavigationTabResponse(
        String key,
        String label,
        String emoji,
        String route,
        NavigationApiMeta api
) {
}
