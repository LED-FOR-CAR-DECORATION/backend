package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistServiceTest {

    @Mock
    private SafeCacheService cacheService;

    @InjectMocks
    private RedisTokenBlacklistService blacklistService;

    @Test
    @DisplayName("UTCID01 [N]: blacklistToken computes SHA-256 and sets key in cache with remaining TTL")
    void blacklistToken_storesHashedTokenInRedisWithTtl() {
        String rawToken = "sample.raw.access.token";
        Duration ttl = Duration.ofMinutes(15);
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:blacklist:" + expectedHash;

        blacklistService.blacklistToken(rawToken, ttl);

        verify(cacheService).set(expectedKey, "REVOKED", ttl);
    }

    @Test
    @DisplayName("UTCID02 [B]: blacklistToken does nothing when TTL is zero")
    void blacklistToken_whenTtlIsZero_doesNothing() {
        String rawToken = "sample.raw.access.token";
        Duration ttl = Duration.ZERO;

        blacklistService.blacklistToken(rawToken, ttl);

        verify(cacheService, never()).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("UTCID03 [A]: blacklistToken catches CacheUnavailableException and logs warning without throwing")
    void blacklistToken_whenCacheUnavailable_logsWarningAndDoesNotThrow() {
        String rawToken = "sample.raw.access.token";
        Duration ttl = Duration.ofMinutes(10);
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:blacklist:" + expectedHash;

        doThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection timeout")))
                .when(cacheService).set(expectedKey, "REVOKED", ttl);

        assertThatCode(() -> blacklistService.blacklistToken(rawToken, ttl))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UTCID04 [N]: isBlacklisted returns true when hashed key exists in Redis cache")
    void isBlacklisted_whenKeyExists_returnsTrue() {
        String rawToken = "valid.blacklisted.token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:blacklist:" + expectedHash;

        when(cacheService.hasKey(expectedKey)).thenReturn(true);

        boolean result = blacklistService.isBlacklisted(rawToken);

        assertThat(result).isTrue();
        verify(cacheService).hasKey(expectedKey);
    }

    @Test
    @DisplayName("UTCID05 [N]: isBlacklisted returns false when hashed key does not exist in Redis cache")
    void isBlacklisted_whenKeyDoesNotExist_returnsFalse() {
        String rawToken = "active.valid.token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:blacklist:" + expectedHash;

        when(cacheService.hasKey(expectedKey)).thenReturn(false);

        boolean result = blacklistService.isBlacklisted(rawToken);

        assertThat(result).isFalse();
        verify(cacheService).hasKey(expectedKey);
    }

    @Test
    @DisplayName("UTCID06 [A]: isBlacklisted fails open (returns false) when Redis cache is unavailable")
    void isBlacklisted_whenCacheUnavailable_failsOpenReturnsFalse() {
        String rawToken = "active.valid.token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:blacklist:" + expectedHash;

        when(cacheService.hasKey(expectedKey))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection refused")));

        boolean result = blacklistService.isBlacklisted(rawToken);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UTCID07 [B]: blacklistToken computes consistent 64-character hex hash prefix auth:blacklist:")
    void blacklistToken_computesConsistentHashWithNamespace() {
        String rawToken = "boundary.test.token.with.special.chars.+=_";
        Duration ttl = Duration.ofSeconds(60);
        String expectedHash = DigestUtils.sha256Hex(rawToken);

        assertThat(expectedHash).hasSize(64);

        blacklistService.blacklistToken(rawToken, ttl);

        verify(cacheService).set("auth:blacklist:" + expectedHash, "REVOKED", ttl);
    }
}
