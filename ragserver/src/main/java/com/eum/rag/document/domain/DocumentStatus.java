package com.eum.rag.document.domain;

public enum DocumentStatus {
    PENDING,
    PARSING,
    PARSED,
    EMBEDDING,
    EMBEDDED,
    INDEXING,
    PROCESSED,
    FAILED
}
