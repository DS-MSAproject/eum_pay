package com.eum.authserver.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {

    private final StringRedisTemplate redisTemplate;

    // Redis 키 구조: email:verify:{email} = 인증코드 (TTL: 10분)
    private static final String CODE_PREFIX      = "email:verify:";
    private static final String VERIFIED_PREFIX  = "email:verified:";
    private static final long   CODE_TTL_MINUTES = 10L;
    private static final long   VERIFIED_TTL_MINUTES = 30L;

    public void save(String email, String code) {
        redisTemplate.opsForValue().set(
                CODE_PREFIX + email,
                code,
                CODE_TTL_MINUTES, TimeUnit.MINUTES
        );
    }

    public Optional<String> find(String email) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(CODE_PREFIX + email)
        );
    }

    public void delete(String email) {
        redisTemplate.delete(CODE_PREFIX + email);
    }

    // 남은 유효 시간(초) 반환
    public long getRemainSeconds(String email) {
        Long ttl = redisTemplate.getExpire(CODE_PREFIX + email, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    public void markVerified(String email) {
        redisTemplate.opsForValue().set(
                VERIFIED_PREFIX + email,
                "true",
                VERIFIED_TTL_MINUTES, TimeUnit.MINUTES
        );
    }

    public boolean isVerified(String email) {
        return "true".equals(redisTemplate.opsForValue().get(VERIFIED_PREFIX + email));
    }

    public void clearVerified(String email) {
        redisTemplate.delete(VERIFIED_PREFIX + email);
    }
}
