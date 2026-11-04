package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.infrastructure.persistence.entity.RefreshTokenEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.RefreshTokenRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridRefreshTokenServiceTest {

    @Mock
    private SafeCacheService cacheService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private HybridRefreshTokenService refreshTokenService;

    @Test
    @DisplayName("UTCID01 [N]: storeRefreshToken caches hashed token in Redis when cache is available")
    void storeRefreshToken_redisAvailable_storesInCache() {
        Long accountId = 101L;
        String rawToken = "sample-raw-refresh-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        refreshTokenService.storeRefreshToken(accountId, rawToken);

        verify(cacheService).set(eq(expectedKey), eq("101"), eq(Duration.ofDays(30)));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("UTCID02 [A]: storeRefreshToken falls back to DB when Redis throws CacheUnavailableException")
    void storeRefreshToken_redisUnavailable_fallsBackToDatabase() {
        Long accountId = 102L;
        String rawToken = "fallback-raw-refresh-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        doThrow(new CacheUnavailableException("Redis is down", new RuntimeException("connection error")))
                .when(cacheService).set(eq(expectedKey), eq("102"), eq(Duration.ofDays(30)));

        refreshTokenService.storeRefreshToken(accountId, rawToken);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenEntity saved = captor.getValue();

        assertThat(saved.getAccountId()).isEqualTo(102L);
        assertThat(saved.getTokenHash()).isEqualTo(expectedHash);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    }

    @Test
    @DisplayName("UTCID03 [N]: validateAndRevoke finds token in Redis, revokes key immediately, and returns true")
    void validateAndRevoke_redisAvailable_tokenExists_revokesAndReturnsTrue() {
        String rawToken = "active-redis-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        when(cacheService.get(expectedKey)).thenReturn("103");

        boolean result = refreshTokenService.validateAndRevoke(rawToken);

        assertThat(result).isTrue();
        verify(cacheService).delete(expectedKey);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("UTCID04 [A]: validateAndRevoke returns false when token does not exist in Redis")
    void validateAndRevoke_redisAvailable_tokenNotFound_returnsFalse() {
        String rawToken = "unknown-redis-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        when(cacheService.get(expectedKey)).thenReturn(null);

        boolean result = refreshTokenService.validateAndRevoke(rawToken);

        assertThat(result).isFalse();
        verify(cacheService, never()).delete(anyString());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("UTCID05 [N]: validateAndRevoke falls back to DB when Redis down, revokes unexpired token and returns true")
    void validateAndRevoke_redisUnavailable_dbTokenValid_revokesAndReturnsTrue() {
        String rawToken = "db-valid-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        when(cacheService.get(expectedKey)).thenThrow(new CacheUnavailableException("Redis connection lost", new RuntimeException("connection error")));

        RefreshTokenEntity entity = RefreshTokenEntity.create(
                105L,
                expectedHash,
                Instant.now().plus(Duration.ofDays(10))
        );
        when(refreshTokenRepository.findByTokenHash(expectedHash)).thenReturn(Optional.of(entity));

        boolean result = refreshTokenService.validateAndRevoke(rawToken);

        assertThat(result).isTrue();
        verify(refreshTokenRepository).delete(entity);
    }

    @Test
    @DisplayName("UTCID06 [A]: validateAndRevoke falls back to DB when Redis down, revokes expired token and returns false")
    void validateAndRevoke_redisUnavailable_dbTokenExpired_revokesAndReturnsFalse() {
        String rawToken = "db-expired-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        when(cacheService.get(expectedKey)).thenThrow(new CacheUnavailableException("Redis connection lost", new RuntimeException("connection error")));

        RefreshTokenEntity entity = RefreshTokenEntity.create(
                106L,
                expectedHash,
                Instant.now().minus(Duration.ofMinutes(5)) // Expired
        );
        when(refreshTokenRepository.findByTokenHash(expectedHash)).thenReturn(Optional.of(entity));

        boolean result = refreshTokenService.validateAndRevoke(rawToken);

        assertThat(result).isFalse();
        verify(refreshTokenRepository).delete(entity);
    }

    @Test
    @DisplayName("UTCID07 [A]: validateAndRevoke falls back to DB when Redis down, returns false when token not in DB")
    void validateAndRevoke_redisUnavailable_dbTokenNotFound_returnsFalse() {
        String rawToken = "db-missing-token";
        String expectedHash = DigestUtils.sha256Hex(rawToken);
        String expectedKey = "auth:refresh:" + expectedHash;

        when(cacheService.get(expectedKey)).thenThrow(new CacheUnavailableException("Redis connection lost", new RuntimeException("connection error")));
        when(refreshTokenRepository.findByTokenHash(expectedHash)).thenReturn(Optional.empty());

        boolean result = refreshTokenService.validateAndRevoke(rawToken);

        assertThat(result).isFalse();
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UTCID08 [B]: Token hashing consistently generates a 64-char hexadecimal SHA-256 hash")
    void tokenHashing_consistentSha256Output() {
        String rawToken = "deterministic-token-string";
        String hash = DigestUtils.sha256Hex(rawToken);

        assertThat(hash)
                .hasSize(64)
                .matches("^[a-f0-9]{64}$");

        refreshTokenService.storeRefreshToken(999L, rawToken);
        verify(cacheService).set(eq("auth:refresh:" + hash), eq("999"), eq(Duration.ofDays(30)));
    }
}
