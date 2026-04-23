package com.eum.authserver.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;

    // Redis 키 구조
    // refresh:{userId}          = refreshToken(UUID)
    // refresh:prev:{userId}     = 이전 refreshToken (탈취 감지용, 30초 TTL)
    // refresh:idx:{refreshToken} = userId (역방향 인덱스 — KEYS 명령어 제거용)
    private static final String PREFIX      = "refresh:";
    private static final String PREV_PREFIX = "refresh:prev:";
    private static final String IDX_PREFIX  = "refresh:idx:";
    private static final long   TTL_DAY     = 7;
    private static final long   PREV_TTL    = 30L;

    // 토큰 저장 — 역방향 인덱스 함께 저장
    public void save(Long userId, String refreshToken) {
        String existing = redisTemplate.opsForValue().get(PREFIX + userId);
        if (existing != null) {
            // 기존 토큰을 prev에 백업
            redisTemplate.opsForValue().set(
                    PREV_PREFIX + userId, existing, PREV_TTL, TimeUnit.SECONDS
            );
            // 기존 역방향 인덱스 삭제
            redisTemplate.delete(IDX_PREFIX + existing);
        }

        // 새 토큰 저장
        redisTemplate.opsForValue().set(
                PREFIX + userId, refreshToken, TTL_DAY, TimeUnit.DAYS
        );
        // 역방향 인덱스 저장 (token → userId)
        redisTemplate.opsForValue().set(
                IDX_PREFIX + refreshToken, String.valueOf(userId), TTL_DAY, TimeUnit.DAYS
        );
    }

    public String find(Long userId) {
        return redisTemplate.opsForValue().get(PREFIX + userId);
    }

    public void delete(Long userId) {
        String token = redisTemplate.opsForValue().get(PREFIX + userId);
        if (token != null) {
            redisTemplate.delete(IDX_PREFIX + token);
        }
        redisTemplate.delete(PREFIX + userId);
        redisTemplate.delete(PREV_PREFIX + userId);
    }

    public boolean exists(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + userId));
    }

    // KEYS 명령어 제거 → 역방향 인덱스로 O(1) 조회
    public Optional<Long> findUserIdByToken(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(IDX_PREFIX + refreshToken);
        if (userId == null) return Optional.empty();
        return Optional.of(Long.parseLong(userId));
    }

    // 탈취 감지 — 이미 rotation된 이전 토큰으로 재시도하는 경우
    public boolean isStolenToken(Long userId, String refreshToken) {
        String prev = redisTemplate.opsForValue().get(PREV_PREFIX + userId);
        return refreshToken.equals(prev);
    }
}