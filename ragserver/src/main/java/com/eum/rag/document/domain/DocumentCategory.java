package com.eum.rag.document.domain;

public enum DocumentCategory {
    FAQ,
    NOTICE,
    POLICY,
    UNKNOWN;

    public static DocumentCategory from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        try {
            return DocumentCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
