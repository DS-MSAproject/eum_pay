package com.eum.searchserver.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

public class GlobalConfig {

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        // 💡 String 전용 시리얼라이저 선언
        RedisSerializer<String> serializer = RedisSerializer.string();

        // 💡 context 생성 시 인자를 비우고, 하위 메서드에서 세팅합니다.
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(serializer)   // Key 시리얼라이저 지정
                .value(serializer) // Value 시리얼라이저 지정
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}


