package com.eum.searchserver.repository;

import com.eum.searchserver.domain.ProductDocument;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import reactor.core.publisher.Flux;

public interface ProductRepository extends ReactiveElasticsearchRepository<ProductDocument, Long> {
    Flux<ProductDocument> findByTitle(String title);
}
