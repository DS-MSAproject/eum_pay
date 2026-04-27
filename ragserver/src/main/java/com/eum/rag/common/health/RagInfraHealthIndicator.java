package com.eum.rag.common.health;

import com.eum.rag.common.config.properties.RagRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ragInfraHealthIndicator")
@RequiredArgsConstructor
public class RagInfraHealthIndicator implements HealthIndicator {

    private final ElasticsearchOperations elasticsearchOperations;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RagRetrievalProperties ragRetrievalProperties;

    @Override
    public Health health() {
        boolean esUp = false;
        boolean redisUp = false;
        boolean indexExists = false;
        String redisPing = "N/A";

        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(ragRetrievalProperties.indexName());
            indexExists = elasticsearchOperations.indexOps(indexCoordinates).exists();
            esUp = true;
        } catch (Exception exception) {
            log.warn("Elasticsearch health check failed", exception);
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            redisPing = connection.ping();
            redisUp = "PONG".equalsIgnoreCase(redisPing);
        } catch (Exception exception) {
            log.warn("Redis health check failed", exception);
        }

        Health.Builder builder = (esUp && redisUp) ? Health.up() : Health.down();
        return builder
                .withDetail("elasticsearch.connected", esUp)
                .withDetail("elasticsearch.index", ragRetrievalProperties.indexName())
                .withDetail("elasticsearch.indexExists", indexExists)
                .withDetail("redis.connected", redisUp)
                .withDetail("redis.ping", redisPing)
                .build();
    }
}
