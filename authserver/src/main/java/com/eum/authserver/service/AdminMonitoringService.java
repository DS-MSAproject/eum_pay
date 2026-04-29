package com.eum.authserver.service;

import com.eum.authserver.dto.admin.KafkaLagResponse;
import com.eum.authserver.dto.admin.LogSearchResponse;
import com.eum.authserver.dto.admin.ServiceHealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminMonitoringService {

    private static final List<String> EUREKA_SERVICES = List.of(
            "AUTHSERVER", "PRODUCTSERVER", "INVENTORYSERVER", "ORDERSERVER",
            "PAYMENTSERVER", "REVIEWSERVER", "CARTSERVER", "SEARCHSERVER",
            "BOARDSERVER", "RAGSERVER"
    );

    private final WebClient.Builder webClientBuilder;
    private final AdminClient kafkaAdminClient;
    private final WebClient elasticsearchWebClient;

    public AdminMonitoringService(WebClient.Builder webClientBuilder,
                                  AdminClient kafkaAdminClient,
                                  @Qualifier("elasticsearchWebClient") WebClient elasticsearchWebClient) {
        this.webClientBuilder = webClientBuilder;
        this.kafkaAdminClient = kafkaAdminClient;
        this.elasticsearchWebClient = elasticsearchWebClient;
    }

    // ── 서비스 헬스 체크 ────────────────────────────────
    public List<ServiceHealthResponse> getServicesHealth() {
        return EUREKA_SERVICES.stream().map(svc -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> health = webClientBuilder.build().get()
                        .uri("lb://" + svc + "/actuator/health")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(3))
                        .block();
                String status = health != null ? (String) health.get("status") : "UNKNOWN";
                return ServiceHealthResponse.builder()
                        .serviceName(svc.toLowerCase())
                        .status(status != null ? status : "UNKNOWN")
                        .checkedAt(LocalDateTime.now())
                        .build();
            } catch (Exception e) {
                log.debug("[Admin] {} health check failed: {}", svc, e.getMessage());
                return ServiceHealthResponse.builder()
                        .serviceName(svc.toLowerCase())
                        .status("DOWN")
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
        }).toList();
    }

    // ── Kafka 컨슈머 그룹 오프셋 랙 ───────────────────
    public List<KafkaLagResponse> getKafkaConsumerLag() {
        List<KafkaLagResponse> result = new ArrayList<>();
        try {
            List<String> groups = kafkaAdminClient.listConsumerGroups().all().get()
                    .stream()
                    .map(g -> g.groupId())
                    .toList();

            for (String groupId : groups) {
                try {
                    Map<TopicPartition, OffsetAndMetadata> offsets =
                            kafkaAdminClient.listConsumerGroupOffsets(groupId)
                                    .partitionsToOffsetAndMetadata().get();

                    if (offsets.isEmpty()) continue;

                    Map<TopicPartition, OffsetSpec> latestQuery = offsets.keySet().stream()
                            .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                            kafkaAdminClient.listOffsets(latestQuery).all().get();

                    for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                        TopicPartition tp = entry.getKey();
                        long current = entry.getValue().offset();
                        ListOffsetsResult.ListOffsetsResultInfo endInfo = endOffsets.get(tp);
                        long end = endInfo != null ? endInfo.offset() : current;
                        result.add(KafkaLagResponse.builder()
                                .groupId(groupId)
                                .topic(tp.topic())
                                .partition(tp.partition())
                                .currentOffset(current)
                                .endOffset(end)
                                .lag(Math.max(0, end - current))
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("[Admin] Kafka lag 조회 실패 (group={}): {}", groupId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Admin] Kafka consumer group 목록 조회 실패: {}", e.getMessage());
        }
        return result;
    }

    // ── ELK 로그 검색 ───────────────────────────────────
    public LogSearchResponse searchLogs(String service, String level,
                                        String keyword, String traceId,
                                        String from, String to, int size) {
        String query = buildBoolQuery(service, level, keyword, traceId, from, to, size, "desc");
        return executeEsSearch(query);
    }

    // ── 주문 타임라인 (orderId로 로그 집계) ─────────────
    public LogSearchResponse getOrderLogTimeline(String orderId) {
        String query = buildMatchPhraseQuery(orderId, 100, "asc");
        return executeEsSearch(query);
    }

    // ── ES 쿼리 실행 ────────────────────────────────────
    @SuppressWarnings("unchecked")
    private LogSearchResponse executeEsSearch(String query) {
        try {
            Map<String, Object> response = elasticsearchWebClient.post()
                    .uri("/filebeat-*/_search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return parseEsResponse(response);
        } catch (Exception e) {
            log.warn("[Admin] ES 로그 검색 실패: {}", e.getMessage());
            return LogSearchResponse.builder().totalHits(0).logs(List.of()).build();
        }
    }

    @SuppressWarnings("unchecked")
    private LogSearchResponse parseEsResponse(Map<String, Object> response) {
        if (response == null) return LogSearchResponse.builder().totalHits(0).logs(List.of()).build();

        Map<String, Object> hits = (Map<String, Object>) response.get("hits");
        if (hits == null) return LogSearchResponse.builder().totalHits(0).logs(List.of()).build();

        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        long totalHits = total != null ? ((Number) total.get("value")).longValue() : 0L;

        List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
        List<LogSearchResponse.LogEntry> entries = hitList == null ? List.of() :
                hitList.stream().map(hit -> {
                    Map<String, Object> src = (Map<String, Object>) hit.get("_source");
                    if (src == null) return null;
                    return LogSearchResponse.LogEntry.builder()
                            .serviceName(str(src, "service_name"))
                            .level(str(src, "level"))
                            .message(str(src, "message"))
                            .traceId(str(src, "traceId"))
                            .timestamp(str(src, "@timestamp"))
                            .build();
                }).filter(e -> e != null).toList();

        return LogSearchResponse.builder().totalHits(totalHits).logs(entries).build();
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private String buildBoolQuery(String service, String level, String keyword,
                                  String traceId, String from, String to, int size, String order) {
        StringBuilder filters = new StringBuilder();
        if (service != null && !service.isBlank())
            filters.append("{\"term\":{\"service_name\":\"").append(service).append("\"}},");
        if (level != null && !level.isBlank())
            filters.append("{\"term\":{\"level\":\"").append(level.toUpperCase()).append("\"}},");
        if (from != null && to != null && !from.isBlank() && !to.isBlank())
            filters.append("{\"range\":{\"@timestamp\":{\"gte\":\"").append(from)
                    .append("\",\"lte\":\"").append(to).append("\"}}},");

        StringBuilder must = new StringBuilder();
        if (keyword != null && !keyword.isBlank())
            must.append("{\"match\":{\"message\":\"").append(keyword).append("\"}},");
        if (traceId != null && !traceId.isBlank())
            must.append("{\"term\":{\"traceId\":\"").append(traceId).append("\"}},");

        String filtersStr = filters.length() > 0
                ? filters.deleteCharAt(filters.length() - 1).toString() : "";
        String mustStr = must.length() > 0
                ? must.deleteCharAt(must.length() - 1).toString() : "{\"match_all\":{}}";

        return "{\"size\":" + Math.min(size, 200) + "," +
                "\"sort\":[{\"@timestamp\":{\"order\":\"" + order + "\"}}]," +
                "\"query\":{\"bool\":{\"must\":[" + mustStr + "]," +
                "\"filter\":[" + filtersStr + "]}}}";
    }

    private String buildMatchPhraseQuery(String phrase, int size, String order) {
        return "{\"size\":" + size + "," +
                "\"sort\":[{\"@timestamp\":{\"order\":\"" + order + "\"}}]," +
                "\"query\":{\"match_phrase\":{\"message\":\"" + phrase + "\"}}}";
    }
}
