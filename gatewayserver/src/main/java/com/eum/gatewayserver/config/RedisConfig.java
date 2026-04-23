package com.eum.gatewayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // ReactiveStringRedisTemplate 명시적 등록
    // application.yml의 Redis 설정(host, port, password)을 사용하는
    // ReactiveRedisConnectionFactory를 주입받아 템플릿 생성
    // Gateway JwtAuthenticationFilter의 블랙리스트 조회에 사용
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        return new ReactiveStringRedisTemplate(
                factory,
                RedisSerializationContext.string()
        );
    }
}