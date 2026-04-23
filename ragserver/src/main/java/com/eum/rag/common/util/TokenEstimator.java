package com.eum.rag.common.util;

import java.util.Arrays;

public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(text.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .count();
    }
}
