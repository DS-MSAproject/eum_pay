package com.eum.reviewserver.service;

import com.eum.reviewserver.dto.response.OrderItemHistoryDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderHistoryClient {

    private static final ParameterizedTypeReference<List<OrderItemHistoryDto>> HISTORY_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public OrderHistoryClient(@Value("${order.service.base-url:http://dseumorders:8989}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public List<OrderItemHistoryDto> getUserOrderHistory(Long userId) {
        List<OrderItemHistoryDto> response = restClient.get()
                .uri("/orders/me/history")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-User-Id", String.valueOf(userId))
                .retrieve()
                .body(HISTORY_TYPE);

        return response == null ? List.of() : response;
    }
}
