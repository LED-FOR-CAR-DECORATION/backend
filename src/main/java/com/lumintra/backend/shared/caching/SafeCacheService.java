package com.lumintra.backend.shared.caching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SafeCacheService {

    private static final Logger log = LoggerFactory.getLogger(SafeCacheService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    // Lightweight Circuit Breaker State
    private volatile long circuitOpenUntil = 0;
    private static final long CIRCUIT_OPEN_DURATION_MS = 15000; // Fast-fail for 15 seconds

    public SafeCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if the circuit is open. If so, fast-fails instantly to route to DB fallback.
     */
    private void checkCircuit() {
        if (System.currentTimeMillis() < circuitOpenUntil) {
            throw new CacheUnavailableException("Redis circuit breaker is OPEN (fast-failing)", null);
        }
    }

    /**
     * Records a failure, trips the circuit breaker, and throws the exception.
     */
    private void handleFailure(RuntimeException ex, String operation) {
        circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_OPEN_DURATION_MS;
        log.warn("Redis failure during '{}'. Circuit breaker OPENED for 15s. Cause: {}", operation, ex.getMessage());
        throw new CacheUnavailableException("Redis is down or timed out, failed to " + operation, ex);
    }

    public boolean hasKey(String key) {
        checkCircuit();
        try {
            boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            log.debug("Cache check [key={}] [hit={}]", key, exists);
            return exists;
        } catch (RuntimeException ex) {
            handleFailure(ex, "check key");
            return false; // Unreachable
        }
    }

    public void set(String key, Object value, Duration ttl) {
        checkCircuit();
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("Cache set [key={}] [value={}] [ttl={}s]", key, value, ttl.getSeconds());
        } catch (RuntimeException ex) {
            handleFailure(ex, "set cache");
        }
    }

    public String get(String key) {
        checkCircuit();
        try {
            Object value = redisTemplate.opsForValue().get(key);
            log.debug("Cache get [key={}] [hit={}]", key, value != null);
            return value != null ? value.toString() : null;
        } catch (RuntimeException ex) {
            handleFailure(ex, "get");
            return null; // Unreachable
        }
    }

    public int increment(String key, Duration ttlIfNew) {
        checkCircuit();
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1) {
                redisTemplate.expire(key, ttlIfNew);
            }
            log.debug("Cache incremented [key={}] [newValue={}]", key, value);
            return value != null ? value.intValue() : 0;
        } catch (RuntimeException ex) {
            handleFailure(ex, "increment");
            return 0; // Unreachable
        }
    }

    public void delete(String key) {
        checkCircuit();
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Cache deleted [key={}] [success={}]", key, deleted);
        } catch (RuntimeException ex) {
            handleFailure(ex, "delete");
        }
    }
}