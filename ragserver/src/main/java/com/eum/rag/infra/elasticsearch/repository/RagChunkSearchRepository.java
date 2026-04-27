package com.eum.rag.infra.elasticsearch.repository;

import com.eum.rag.common.config.properties.RagRetrievalProperties;
import com.eum.rag.infra.elasticsearch.document.RagChunkDocument;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagChunkSearchRepository {

    private final ElasticsearchOperations operations;
    private final RagRetrievalProperties ragRetrievalProperties;

    public void saveAll(List<RagChunkDocument> documents) {
        saveAll(documents, false);
    }

    public void saveAll(List<RagChunkDocument> documents, boolean refresh) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        // 문서당 청크가 많을 때도 처리량을 유지하기 위해 bulk 인덱싱을 사용한다.
        IndexCoordinates index = indexCoordinates();
        List<IndexQuery> bulkQueries = documents.stream()
                .map(document -> new IndexQueryBuilder()
                        .withId(document.getChunkId())
                        .withObject(document)
                        .build())
                .toList();

        operations.bulkIndex(bulkQueries, index);
        if (refresh) {
            operations.indexOps(index).refresh();
        }
    }

    public List<RagChunkDocument> lexicalSearch(String question, int size) {
        // BM25 검색: text 필드 기준 정합도를 계산하며 active=true 데이터만 조회한다.
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.match(mm -> mm.field("text").query(question)))
                        .filter(f -> f.term(t -> t.field("active").value(true)))))
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<RagChunkDocument> hits = operations.search(query, RagChunkDocument.class, indexCoordinates());
        return hits.stream().map(SearchHit::getContent).toList();
    }

    public List<RagChunkDocument> findActiveCandidates(int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("active").value(true)))))
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<RagChunkDocument> hits = operations.search(query, RagChunkDocument.class, indexCoordinates());
        return hits.stream().map(SearchHit::getContent).toList();
    }

    public List<RagChunkDocument> semanticSearch(List<Float> queryVector, int size) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        // Semantic 검색: 임베딩 코사인 유사도로 점수화하고 active=true 조건을 강제한다.
        String queryVectorJson = queryVector.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        String queryJson = """
                {
                  "script_score": {
                    "query": {
                      "bool": {
                        "filter": [
                          { "term": { "active": true } }
                        ]
                      }
                    },
                    "script": {
                      "source": "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
                      "params": {
                        "query_vector": [%s]
                      }
                    }
                  }
                }
                """.formatted(queryVectorJson);

        StringQuery query = new StringQuery(queryJson);
        query.setPageable(PageRequest.of(0, size));

        SearchHits<RagChunkDocument> hits = operations.search(query, RagChunkDocument.class, indexCoordinates());
        return hits.stream().map(SearchHit::getContent).toList();
    }

    public List<RagChunkDocument> findByDocumentIdAndVersion(String documentId, int version) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("documentId").value(documentId)))
                        .filter(f -> f.term(t -> t.field("version").value(version)))))
                .withPageable(PageRequest.of(0, 10_000))
                .build();

        SearchHits<RagChunkDocument> hits = operations.search(query, RagChunkDocument.class, indexCoordinates());
        return hits.stream().map(SearchHit::getContent).toList();
    }

    public List<RagChunkDocument> findByDocumentIdAndActive(String documentId, boolean active) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("documentId").value(documentId)))
                        .filter(f -> f.term(t -> t.field("active").value(active)))))
                .withPageable(PageRequest.of(0, 10_000))
                .build();

        SearchHits<RagChunkDocument> hits = operations.search(query, RagChunkDocument.class, indexCoordinates());
        return hits.stream().map(SearchHit::getContent).toList();
    }

    public int findNextVersion(String documentId) {
        // document_id 단위 버전은 단조 증가하도록 계산한다.
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("documentId").value(documentId)))))
                .withPageable(PageRequest.of(0, 10_000))
                .build();

        SearchHits<RagChunkDocument> hits = operations.search(query, RagChunkDocument.class, indexCoordinates());
        int maxVersion = hits.stream()
                .map(SearchHit::getContent)
                .map(RagChunkDocument::getVersion)
                .filter(version -> version != null)
                .max(Comparator.naturalOrder())
                .orElse(0);

        return maxVersion + 1;
    }

    public void deleteByDocumentIdAndVersion(String documentId, int version) {
        List<RagChunkDocument> targets = findByDocumentIdAndVersion(documentId, version);
        if (targets.isEmpty()) {
            return;
        }

        IndexCoordinates index = indexCoordinates();
        targets.forEach(target -> operations.delete(target.getChunkId(), index));
        operations.indexOps(index).refresh();
    }

    public void refreshIndex() {
        operations.indexOps(indexCoordinates()).refresh();
    }

    private IndexCoordinates indexCoordinates() {
        return IndexCoordinates.of(ragRetrievalProperties.indexName());
    }
}
