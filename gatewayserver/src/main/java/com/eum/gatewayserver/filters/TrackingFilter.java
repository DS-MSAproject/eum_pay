package com.eum.gatewayserver.filters;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE) // 💡 추적은 무조건 가장 먼저 시작해야 함
public class TrackingFilter implements GlobalFilter {
	private final FilterUtils filterUtils;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

		// 1. 기존 ID 확인 (Micrometer 혹은 외부 유입)
		String correlationId = filterUtils.getCorrelationId(requestHeaders);

		if (correlationId == null) {
			correlationId = java.util.UUID.randomUUID().toString();
			exchange = filterUtils.setCorrelationId(exchange, correlationId);
		}

		// 2. Reactor Context에 담아 비동기 전파 보장
		final String finalId = correlationId;
		return chain.filter(exchange)
				.contextWrite(context -> context.put("correlationId", finalId));
	}
}