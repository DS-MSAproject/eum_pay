package com.eum.authserver.service;

import com.eum.authserver.dto.admin.DashboardSummaryResponse;
import com.eum.authserver.dto.admin.KafkaLagResponse;
import com.eum.authserver.dto.admin.ServiceHealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminMonitoringService adminMonitoringService;
    private final WebClient.Builder webClientBuilder;

    public DashboardSummaryResponse getSummary() {
        List<ServiceHealthResponse> healthList = adminMonitoringService.getServicesHealth();
        int servicesUp = (int) healthList.stream().filter(s -> "UP".equals(s.getStatus())).count();

        List<KafkaLagResponse> kafkaLags = tryGetKafkaLag();
        Map<String, Long> kafkaLagSummary = kafkaLags.stream()
                .collect(Collectors.groupingBy(KafkaLagResponse::getTopic,
                        Collectors.summingLong(KafkaLagResponse::getLag)));

        OrderStats orderStats = fetchOrderStats();
        PaymentStats paymentStats = fetchPaymentStats();
        long lowStockCount = fetchLowStockCount();

        return DashboardSummaryResponse.builder()
                .todayOrders(orderStats.todayOrders)
                .totalOrders(orderStats.totalOrders)
                .failedOrders(orderStats.failedOrders)
                .orderStatusBreakdown(orderStats.statusBreakdown)
                .todayRevenue(paymentStats.todayRevenue)
                .totalRevenue(paymentStats.totalRevenue)
                .reconciliationIssues(paymentStats.reconciliationIssues)
                .lowStockCount(lowStockCount)
                .healthyServices(servicesUp)
                .totalServices(healthList.size())
                .kafkaLagSummary(kafkaLagSummary)
                .build();
    }

    @SuppressWarnings("unchecked")
    private OrderStats fetchOrderStats() {
        try {
            Map<String, Object> body = webClientBuilder.build().get()
                    .uri("lb://DSEUM-ORDER/admin/orders/stats")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            if (body == null) return OrderStats.empty();
            long todayOrders = toLong(body.get("todayOrders"));
            long totalOrders = toLong(body.get("totalOrders"));
            long failedOrders = toLong(body.get("failedOrders"));
            Map<String, Long> breakdown = body.get("orderStatusBreakdown") instanceof Map<?,?> m
                    ? m.entrySet().stream().collect(Collectors.toMap(
                            e -> (String) e.getKey(),
                            e -> toLong(e.getValue())))
                    : Map.of();
            return new OrderStats(todayOrders, totalOrders, failedOrders, breakdown);
        } catch (Exception e) {
            log.warn("[Dashboard] 주문 통계 조회 실패: {}", e.getMessage());
            return OrderStats.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private PaymentStats fetchPaymentStats() {
        try {
            Map<String, Object> body = webClientBuilder.build().get()
                    .uri("lb://DSEUM-PAYMENT/admin/payments/stats")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            if (body == null) return PaymentStats.empty();
            return new PaymentStats(
                    toLong(body.get("todayRevenue")),
                    toLong(body.get("totalRevenue")),
                    toLong(body.get("reconciliationIssues")));
        } catch (Exception e) {
            log.warn("[Dashboard] 결제 통계 조회 실패: {}", e.getMessage());
            return PaymentStats.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private long fetchLowStockCount() {
        try {
            Map<String, Object> body = webClientBuilder.build().get()
                    .uri("lb://DSEUM-INVENTORY/admin/inventory/stats")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return body != null ? toLong(body.get("lowStockCount")) : 0L;
        } catch (Exception e) {
            log.warn("[Dashboard] 재고 통계 조회 실패: {}", e.getMessage());
            return 0L;
        }
    }

    private List<KafkaLagResponse> tryGetKafkaLag() {
        try {
            return adminMonitoringService.getKafkaConsumerLag();
        } catch (Exception e) {
            log.warn("[Dashboard] Kafka lag 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private record OrderStats(long todayOrders, long totalOrders, long failedOrders, Map<String, Long> statusBreakdown) {
        static OrderStats empty() { return new OrderStats(0, 0, 0, Map.of()); }
    }

    private record PaymentStats(long todayRevenue, long totalRevenue, long reconciliationIssues) {
        static PaymentStats empty() { return new PaymentStats(0, 0, 0); }
    }
}
