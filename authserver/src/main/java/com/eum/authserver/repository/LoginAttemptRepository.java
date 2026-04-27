package com.eum.authserver.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

    private final StringRedisTemplate redisTemplate;

    // Redis 키 구조 — username 기반 (로그인이 username 기반이므로 통일)
    // login:fail:{username}  = 실패 횟수 (TTL: 잠금 시간)
    // login:lock:{username}  = 잠금 여부 (TTL: 잠금 시간)
    private static final String FAIL_PREFIX  = "login:fail:";
    private static final String LOCK_PREFIX  = "login:lock:";

    private static final int  MAX_ATTEMPTS = 5;     // 실패 횟수 5번
    private static final long LOCK_SECONDS  = 900L; // 잠금 시간: 15분

    // 실패 횟수 증가 — 반환값: 누적 실패 횟수
    public long incrementFailCount(String username) {
        String key = FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) count = 1L;

        // 첫 실패 시 TTL 설정
        if (count == 1) {
            redisTemplate.expire(key, LOCK_SECONDS, TimeUnit.SECONDS);
        }

        // 최대 횟수 초과 시 잠금 키 등록
        if (count >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(
                    LOCK_PREFIX + username, "1", LOCK_SECONDS, TimeUnit.SECONDS
            );
        }

        return count;
    }

    // 잠금 여부 확인
    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + username));
    }

    // 잠금 남은 시간 (초)
    public long getLockRemainSeconds(String username) {
        Long ttl = redisTemplate.getExpire(LOCK_PREFIX + username, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    // 로그인 성공 시 실패 기록 초기화
    public void clearFailCount(String username) {
        redisTemplate.delete(FAIL_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }

    // 현재 실패 횟수 조회
    public long getFailCount(String username) {
        String value = redisTemplate.opsForValue().get(FAIL_PREFIX + username);
        return value != null ? Long.parseLong(value) : 0;
    }
}