package com.eum.searchserver.global.config;

import com.eum.searchserver.domain.FaqDocument;
import com.eum.searchserver.domain.NoticeDocument;
import com.eum.searchserver.domain.ProductDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ReactiveIndexOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Configuration
public class ReactiveElasticsearchConfig extends ReactiveElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String elasticsearchUri;

    @Override
    public ClientConfiguration clientConfiguration() {
        String cleanUri = elasticsearchUri.replace("http://", "").replace("https://", "");

        return ClientConfiguration.builder()
                .connectedTo(cleanUri)
                .withConnectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * [테스트 환경 전용] 인덱스 완전 초기화 Bean
     * Product와 Notice 인덱스를 모두 삭제 후 설정(settings/mappings)에 맞춰 재생성합니다.
     */
    @Bean
    public CommandLineRunner indexInitializer(ReactiveElasticsearchOperations operations) {
        return args -> {
            Class<?>[] documents = {ProductDocument.class, NoticeDocument.class, FaqDocument.class};

            Flux.fromArray(documents)
                    .flatMap(docClass -> {
                        ReactiveIndexOperations indexOps = operations.indexOps(docClass);

                        // 1. 인덱스가 존재하는지 확인
                        return indexOps.exists()
                                .flatMap(exists -> {
                                    if (exists) {
                                        // 💡 인덱스가 이미 있다면? 삭제하지 않고 매핑만 최신화 (Update Mapping)
                                        // 새로운 필드가 추가되었을 때 기존 데이터를 유지하며 필드만 늘려줍니다.
                                        return indexOps.putMapping()
                                                .doOnSuccess(unused -> log.info("### [ES 업데이트] {} 매핑 최신화 완료 ###", docClass.getSimpleName()));
                                    } else {
                                        // 💡 인덱스가 없다면? 처음이니까 생성 (Settings + Mappings)
                                        return indexOps.createWithMapping()
                                                .doOnSuccess(unused -> log.info("### [ES 생성] {} 신규 생성 완료 ###", docClass.getSimpleName()));
                                    }
                                });
                    })
                    .doOnComplete(() -> log.info("### [ES 초기화] 모든 인덱스 상태가 완벽하게 동기화되었습니다. ###"))
                    .subscribe(); // 리액티브 체인 실행
        };
    }
}
