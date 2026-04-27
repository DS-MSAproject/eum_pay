package com.eum.rag.document.event;

import com.eum.rag.document.domain.DocumentCategory;

public record DocumentIngestionRequestedEvent(
        String documentId,
        String filename,
        DocumentCategory category,
        byte[] fileContent
) {
}
