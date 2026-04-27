package com.eum.gatewayserver.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

@Slf4j // 💡 로그를 위해 추가
@Component
public class FilterUtils {

	public static final String CORRELATION_ID = "tmx-correlation-id";

	public String getCorrelationId(HttpHeaders requestHeaders) {
		String id = Optional.ofNullable(requestHeaders.get(CORRELATION_ID))
				.flatMap(list -> list.stream().findFirst())
				.orElse(null);

		// 💡 [로그 1] 외부에서 유입된 ID가 있는지 확인
		log.info("[FilterUtils] 유입된 Correlation-ID: {}", id);
		return id;
	}

	public ServerWebExchange setRequestHeader(ServerWebExchange exchange,
											  String name, String value) {
		// 💡 [로그 2] 헤더가 실제로 Mutation 되는 시점을 기록
		log.info("[FilterUtils] 요청 헤더 설정 - Key: {}, Value: {}", name, value);

		return exchange.mutate()
				.request(r -> r.header(name, value))
				.build();
	}

	public ServerWebExchange setCorrelationId(ServerWebExchange exchange,
											  String correlationId) {
		return this.setRequestHeader(exchange, CORRELATION_ID, correlationId);
	}
}