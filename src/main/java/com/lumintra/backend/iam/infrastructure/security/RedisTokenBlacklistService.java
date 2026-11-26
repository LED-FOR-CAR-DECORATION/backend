// src/main/java/com/lumintra/backend/iam/infrastructure/security/RedisTokenBlacklistService.java
package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);
    private final SafeCacheService cacheService;

    @Override
    public void blacklistToken(String token, Duration ttl) {
        if (ttl.isZero()) return;
        String hash = DigestUtils.sha256Hex(token);
        try {
            cacheService.set("auth:blacklist:" + hash, "REVOKED", ttl);
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, failed to blacklist access token [hashPrefix={}]", hash.substring(0, 8));
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        String hash = DigestUtils.sha256Hex(token);
        try {
            return cacheService.hasKey("auth:blacklist:" + hash);
        } catch (CacheUnavailableException ex) {
            // If cache is down, do not block the entire API.
            // Short-lived JWTs will expire natively in <15 minutes.
            return false;
        }
    }
}