package com.eum.gatewayserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class CsrfController {

    // 프론트엔드가 앱 시작 시 이 엔드포인트를 GET 호출하면
    // csrfCookieWebFilter가 XSRF-TOKEN 쿠키를 자동으로 발급
    // 이후 POST/PUT/DELETE 요청 시 프론트엔드는 쿠키에서 값을 읽어
    // X-XSRF-TOKEN 헤더에 담아 전송해야 함
    @GetMapping("/csrf")
    public Mono<Map<String, String>> getCsrfToken() {
        log.info("[GATEWAY] CSRF Token 발급 요청");
        return Mono.just(Map.of(
                "message", "XSRF-TOKEN cookie has been set. Include it as X-XSRF-TOKEN header in subsequent requests."
        ));
    }
}
