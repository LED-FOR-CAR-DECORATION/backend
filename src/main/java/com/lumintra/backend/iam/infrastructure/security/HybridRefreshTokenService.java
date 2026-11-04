package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RefreshTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.RefreshTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class HybridRefreshTokenService implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(HybridRefreshTokenService.class);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final String PREFIX = "auth:refresh:";

    private final SafeCacheService cacheService;
    private final RefreshTokenRepository refreshTokenRepository;

    private String hashToken(String rawToken) {
        return DigestUtils.sha256Hex(rawToken);
    }

    @Override
    @Transactional
    public void storeRefreshToken(Long accountId, String rawToken) {
        String hash = hashToken(rawToken);
        String key = PREFIX + hash;

        try {
            cacheService.set(key, accountId.toString(), REFRESH_TTL);
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, storing Refresh Token in DB [accountId={}]", accountId);
            RefreshTokenEntity entity = RefreshTokenEntity.create(
                    accountId,
                    hash,
                    Instant.now().plus(REFRESH_TTL));
            refreshTokenRepository.save(entity);
        }
    }

    @Override
    @Transactional
    public boolean validateAndRevoke(String rawToken) {
        String hash = hashToken(rawToken);
        String key = PREFIX + hash;

        try {
            String accountIdStr = cacheService.get(key);
            if (accountIdStr != null) {
                cacheService.delete(key); // Revoke upon use (Token Rotation)
                return true;
            }
            return false;
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, validating Refresh Token from DB [hashPrefix={}]", hash.substring(0, 8));
            return refreshTokenRepository.findByTokenHash(hash)
                    .map(token -> {
                        refreshTokenRepository.delete(token); // Revoke upon use
                        return token.getExpiresAt().isAfter(Instant.now());
                    }).orElse(false);
        }
    }
}