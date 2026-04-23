package com.eum.rag.infra.elasticsearch.repository;

import com.eum.rag.common.config.properties.RagAiProperties;
import com.eum.rag.common.config.properties.RagRetrievalProperties;
import com.eum.rag.infra.elasticsearch.document.RagChunkDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagIndexInitializer {

    private final ElasticsearchOperations operations;
    private final RagAiProperties ragAiProperties;
    private final RagRetrievalProperties ragRetrievalProperties;

    public void initialize() {
        IndexCoordinates indexCoordinates = IndexCoordinates.of(ragRetrievalProperties.indexName());
        IndexOperations indexOps = operations.indexOps(indexCoordinates);

        if (!indexOps.exists()) {
            indexOps.create();
        }
        indexOps.putMapping(buildMapping());
    }

    private Document buildMapping() {
        Document mapping = Document.create();
        Document properties = Document.create();

        properties.put("chunkId", field("keyword"));
        properties.put("documentId", field("keyword"));
        properties.put("filename", field("keyword"));
        properties.put("category", field("keyword"));
        properties.put("version", field("integer"));
        properties.put("chunkIndex", field("integer"));
        properties.put("text", field("text"));
        properties.put("active", field("boolean"));
        properties.put("createdAt", field("date"));

        Document embedding = field("dense_vector");
        embedding.put("dims", ragAiProperties.embeddingDimension());
        embedding.put("index", true);
        embedding.put("similarity", "cosine");
        properties.put("embedding", embedding);

        mapping.put("properties", properties);
        return mapping;
    }

    private Document field(String type) {
        Document field = Document.create();
        field.put("type", type);
        return field;
    }
}
