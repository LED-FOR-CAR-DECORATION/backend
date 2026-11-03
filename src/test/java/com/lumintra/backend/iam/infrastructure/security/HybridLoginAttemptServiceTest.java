package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
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
class HybridLoginAttemptServiceTest {

    @Mock
    private SafeCacheService cacheService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private HybridLoginAttemptService loginAttemptService;

    // --- isAccountLocked ---

    @Test
    @DisplayName("UTCID01 [N]: isAccountLocked returns true when Redis lockout key exists")
    void isAccountLocked_redisKeyExists_returnsTrue() {
        String email = "locked@example.com";
        when(cacheService.hasKey("auth:lockout:" + email)).thenReturn(true);

        boolean locked = loginAttemptService.isAccountLocked(email);

        assertThat(locked).isTrue();
        verify(cacheService).hasKey("auth:lockout:" + email);
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    @DisplayName("UTCID02 [N]: isAccountLocked returns false when Redis lockout key does not exist")
    void isAccountLocked_redisKeyDoesNotExist_returnsFalse() {
        String email = "normal@example.com";
        when(cacheService.hasKey("auth:lockout:" + email)).thenReturn(false);

        boolean locked = loginAttemptService.isAccountLocked(email);

        assertThat(locked).isFalse();
        verify(cacheService).hasKey("auth:lockout:" + email);
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    @DisplayName("UTCID03 [N]: isAccountLocked falls back to DB when Redis down and lockoutUntil is active")
    void isAccountLocked_redisDown_dbLockoutActive_returnsTrue() {
        String email = "db.locked@example.com";
        when(cacheService.hasKey("auth:lockout:" + email))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")));

        UserAccountEntity user = new UserAccountEntity();
        user.setEmail(email);
        user.setLockoutUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(user));

        boolean locked = loginAttemptService.isAccountLocked(email);

        assertThat(locked).isTrue();
        verify(userAccountRepository).findByEmail(email);
    }

    @Test
    @DisplayName("UTCID04 [A]: isAccountLocked falls back to DB when Redis down and lockoutUntil is expired or null")
    void isAccountLocked_redisDown_dbLockoutExpired_returnsFalse() {
        String email = "db.expired@example.com";
        when(cacheService.hasKey("auth:lockout:" + email))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")));

        UserAccountEntity user = new UserAccountEntity();
        user.setEmail(email);
        user.setLockoutUntil(Instant.now().minus(Duration.ofMinutes(1)));
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(user));

        boolean locked = loginAttemptService.isAccountLocked(email);

        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("UTCID05 [A]: isAccountLocked falls back to DB when Redis down and user not found")
    void isAccountLocked_redisDown_userNotFound_returnsFalse() {
        String email = "unknown@example.com";
        when(cacheService.hasKey("auth:lockout:" + email))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")));
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        boolean locked = loginAttemptService.isAccountLocked(email);

        assertThat(locked).isFalse();
    }

    // --- registerFailedAttempt ---

    @Test
    @DisplayName("UTCID06 [N]: registerFailedAttempt increments Redis below max attempts (count=3)")
    void registerFailedAttempt_redisBelowThreshold_returnsCount() {
        String email = "user@example.com";
        when(cacheService.increment(eq("auth:attempts:" + email), eq(Duration.ofMinutes(15)))).thenReturn(3);

        int attempts = loginAttemptService.registerFailedAttempt(email);

        assertThat(attempts).isEqualTo(3);
        verify(cacheService, never()).set(any(), any(), any());
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    @DisplayName("UTCID07 [B]: registerFailedAttempt locks account at max attempts boundary (count=5)")
    void registerFailedAttempt_redisHitsThreshold_setsLockoutAndClearsAttempts() {
        String email = "boundary@example.com";
        when(cacheService.increment(eq("auth:attempts:" + email), eq(Duration.ofMinutes(15)))).thenReturn(5);

        int attempts = loginAttemptService.registerFailedAttempt(email);

        assertThat(attempts).isEqualTo(5);
        verify(cacheService).set("auth:lockout:" + email, "LOCKED", Duration.ofMinutes(15));
        verify(cacheService).delete("auth:attempts:" + email);
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    @DisplayName("UTCID08 [N]: registerFailedAttempt falls back to DB below threshold when Redis is down")
    void registerFailedAttempt_redisDown_dbIncrementBelowThreshold() {
        String email = "fallback@example.com";
        when(cacheService.increment(any(), any()))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")));

        UserAccountEntity user = new UserAccountEntity();
        user.setEmail(email);
        user.setFailedAttempts(2);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(user));

        int attempts = loginAttemptService.registerFailedAttempt(email);

        assertThat(attempts).isEqualTo(3);
        assertThat(user.getFailedAttempts()).isEqualTo(3);
        assertThat(user.getLockoutUntil()).isNull();
        verify(userAccountRepository).save(user);
    }

    @Test
    @DisplayName("UTCID09 [B]: registerFailedAttempt falls back to DB and sets lockout at boundary 5")
    void registerFailedAttempt_redisDown_dbIncrementHitsLockoutThreshold() {
        String email = "fallback.lock@example.com";
        when(cacheService.increment(any(), any()))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")));

        UserAccountEntity user = new UserAccountEntity();
        user.setEmail(email);
        user.setFailedAttempts(4);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(user));

        int attempts = loginAttemptService.registerFailedAttempt(email);

        assertThat(attempts).isEqualTo(5);
        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockoutUntil()).isNotNull().isAfter(Instant.now());
        verify(userAccountRepository).save(user);
    }

    @Test
    @DisplayName("UTCID10 [A]: registerFailedAttempt falls back to DB when user does not exist")
    void registerFailedAttempt_redisDown_userNotFound_returnsZero() {
        String email = "unknown@example.com";
        when(cacheService.increment(any(), any()))
                .thenThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")));
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        int attempts = loginAttemptService.registerFailedAttempt(email);

        assertThat(attempts).isZero();
        verify(userAccountRepository, never()).save(any());
    }

    // --- resetAttempts ---

    @Test
    @DisplayName("UTCID11 [N]: resetAttempts deletes Redis attempt and lockout keys")
    void resetAttempts_redisSuccess() {
        String email = "reset@example.com";

        loginAttemptService.resetAttempts(email);

        verify(cacheService).delete("auth:attempts:" + email);
        verify(cacheService).delete("auth:lockout:" + email);
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    @DisplayName("UTCID12 [N]: resetAttempts falls back to DB when Redis down and resets failedAttempts & lockoutUntil")
    void resetAttempts_redisDown_resetsDbFields() {
        String email = "reset.db@example.com";
        doThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")))
                .when(cacheService).delete("auth:attempts:" + email);

        UserAccountEntity user = new UserAccountEntity();
        user.setEmail(email);
        user.setFailedAttempts(4);
        user.setLockoutUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(user));

        loginAttemptService.resetAttempts(email);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockoutUntil()).isNull();
        verify(userAccountRepository).save(user);
    }

    @Test
    @DisplayName("UTCID13 [A]: resetAttempts falls back to DB when user not found gracefully")
    void resetAttempts_redisDown_userNotFound_doesNothing() {
        String email = "unknown@example.com";
        doThrow(new CacheUnavailableException("Redis DOWN", new RuntimeException("connection error")))
                .when(cacheService).delete("auth:attempts:" + email);
        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        loginAttemptService.resetAttempts(email);

        verify(userAccountRepository, never()).save(any());
    }
}
