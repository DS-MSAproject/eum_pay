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
            "AUTHSERVER", "DSEUM-PRODUCT", "DSEUM-INVENTORY", "DSEUM-ORDER",
            "DSEUM-PAYMENT", "DSEUM-REVIEW", "DSEUM-CART", "DSEUM-SEARCH",
            "DSEUM-BOARD", "RAGSERVER"
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

    // ── 서비스 헬스 체크 (병렬) ────────────────────────
    public List<ServiceHealthResponse> getServicesHealth() {
        WebClient client = webClientBuilder.build();
        return EUREKA_SERVICES.parallelStream().map(svc -> {
            String base = "lb://" + svc;
            try {
                long start = System.currentTimeMillis();

                @SuppressWarnings("unchecked")
                Map<String, Object> health = client.get()
                        .uri(base + "/actuator/health")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(3))
                        .block();

                long responseTimeMs = System.currentTimeMillis() - start;
                String status = health != null ? (String) health.get("status") : "UNKNOWN";

                // Prometheus 텍스트에서 메트릭 파싱
                String prom = fetchPrometheusText(client, base);

                Double cpu     = parseMetricFirst(prom, "system_cpu_usage", null);
                Double memUsed = parseMetricPositiveSum(prom, "jvm_memory_used_bytes", "area=\"heap\"");
                Double memMax  = parseMetricPositiveSum(prom, "jvm_memory_max_bytes", "area=\"heap\"");

                Double cpuPct = cpu != null ? Math.round(cpu * 1000.0) / 10.0 : null;
                Long usedMb   = memUsed != null ? (long) (memUsed / 1024 / 1024) : null;
                Long maxMb    = (memMax != null && memMax > 0) ? (long) (memMax / 1024 / 1024) : null;
                Double memPct = (usedMb != null && maxMb != null && maxMb > 0)
                        ? Math.round((double) usedMb / maxMb * 1000.0) / 10.0 : null;

                return ServiceHealthResponse.builder()
                        .serviceName(svc.toLowerCase())
                        .status(status != null ? status : "UNKNOWN")
                        .cpuUsage(cpuPct)
                        .memoryUsagePercent(memPct)
                        .memoryUsedMb(usedMb)
                        .memoryMaxMb(maxMb)
                        .responseTimeMs(responseTimeMs)
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

    private String fetchPrometheusText(WebClient client, String base) {
        try {
            return client.get()
                    .uri(base + "/actuator/prometheus")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception e) {
            return null;
        }
    }

    // Prometheus 텍스트에서 첫 번째 매칭 값 반환
    private Double parseMetricFirst(String text, String metricName, String requiredLabel) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            if (line.startsWith("#") || line.isBlank()) continue;
            Double val = parseLine(line, metricName, requiredLabel);
            if (val != null) return val;
        }
        return null;
    }

    // Prometheus 텍스트에서 양수 값들의 합 반환 (메모리: -1 제외)
    private Double parseMetricPositiveSum(String text, String metricName, String requiredLabel) {
        if (text == null) return null;
        double sum = 0;
        boolean found = false;
        for (String line : text.split("\n")) {
            if (line.startsWith("#") || line.isBlank()) continue;
            Double val = parseLine(line, metricName, requiredLabel);
            if (val != null && val > 0) { sum += val; found = true; }
        }
        return found ? sum : null;
    }

    private Double parseLine(String line, String metricName, String requiredLabel) {
        String name = line.contains("{") ? line.substring(0, line.indexOf('{')) : line.split("\\s+")[0];
        if (!name.equals(metricName)) return null;
        if (requiredLabel != null && !line.contains(requiredLabel)) return null;
        try {
            String valueStr = line.contains("}")
                    ? line.substring(line.lastIndexOf('}') + 1).trim().split("\\s+")[0]
                    : line.split("\\s+")[1];
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return null;
        }
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
