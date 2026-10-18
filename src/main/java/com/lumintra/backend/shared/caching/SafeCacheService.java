package com.lumintra.backend.shared.caching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class SafeCacheService {

    private static final Logger log = LoggerFactory.getLogger(SafeCacheService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    public SafeCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable, degraded hasKey to false [key={}]", key);
            return false;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable, skipped setting cache [key={}]", key);
        }
    }

    public int increment(String key, Duration ttlIfNew) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1) {
                redisTemplate.expire(key, ttlIfNew);
            }
            return value != null ? value.intValue() : 0;
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable, skipped increment [key={}]", key);
            return 0; // Degrade gracefully
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable, skipped delete [key={}]", key);
        }
    }
}
