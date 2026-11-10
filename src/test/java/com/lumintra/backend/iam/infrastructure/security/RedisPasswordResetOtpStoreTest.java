package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.shared.exception.iam.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RedisPasswordResetOtpStoreTest {

    @Mock
    StringRedisTemplate redis;

    RedisPasswordResetOtpStore store;

    @BeforeEach
    void setup() {
        store = new RedisPasswordResetOtpStore(redis, "test-secret-only-not-a-real-key");
    }

    @Test
    @DisplayName("UTCID01 [N]: Store protected OTP and namespaced keys in one atomic script")
    @SuppressWarnings("unchecked")
    void protectedChallenge() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenReturn(1L);
        store.issue("customer@example.com", "001234");
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(eq(RedisPasswordResetOtpStore.ISSUE), keys.capture(), digest.capture());
        assertThat(keys.getValue()).hasSize(4).allSatisfy(k -> assertThat(k).startsWith("auth:password-reset:{").doesNotContain("customer@example.com"));
        assertThat(digest.getValue()).matches("[a-f0-9]{64}").doesNotContain("001234");
    }

    @Test
    @DisplayName("UTCID02 [N]: Unknown email counts toward limits without storing a challenge")
    void unknown() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), eq(""))).thenReturn(1L);
        store.issue("missing@example.com", null);
        verify(redis).execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), eq(""));
    }

    @Test
    @DisplayName("UTCID03 [B]: Redis denial maps to a reset lock")
    void locked() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenReturn(0L);
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetLockedException.class);
    }

    @Test
    @DisplayName("UTCID04 [A]: Redis failure is fail-closed and preserves cause")
    void outage() {
        var cause = new DataAccessResourceFailureException("offline");
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenThrow(cause);
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetUnavailableException.class).hasCause(cause);
    }

    @Test
    @DisplayName("UTCID05 [A]: Missing script result is not success")
    void nullResult() {
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetUnavailableException.class);
    }

    @Test
    @DisplayName("UTCID06 [A]: Unexpected script result is not success")
    void unexpectedResult() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenReturn(2L);
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetUnavailableException.class);
    }

    @Test
    @DisplayName("UTCID07 [N]: validateAndConsume returns true when OTP matches stored digest")
    @SuppressWarnings("unchecked")
    void validateSuccess() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), anyList(), anyString())).thenReturn(1L);

        boolean result = store.validateAndConsume("user@example.com", "123456");

        assertThat(result).isTrue();
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), keys.capture(), digest.capture());
        assertThat(keys.getValue()).hasSize(2).allSatisfy(k -> assertThat(k).startsWith("auth:password-reset:{"));
        assertThat(digest.getValue()).matches("[a-f0-9]{64}").doesNotContain("123456");
    }

    @Test
    @DisplayName("UTCID08 [A]: validateAndConsume throws InvalidOtpException on OTP mismatch")
    void validateMismatch() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), anyList(), anyString())).thenReturn(0L);

        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", "999999"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired OTP.");
    }

    @Test
    @DisplayName("UTCID09 [A]: validateAndConsume throws InvalidOtpException when OTP expired or missing")
    void validateExpiredOrMissing() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), anyList(), anyString())).thenReturn(-1L);

        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired OTP.");
    }

    @Test
    @DisplayName("UTCID10 [A]: validateAndConsume throws MaxOtpAttemptsException when retry threshold exceeded")
    void validateMaxAttemptsExceeded() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), anyList(), anyString())).thenReturn(-2L);

        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", "123456"))
                .isInstanceOf(MaxOtpAttemptsException.class)
                .hasMessage("Maximum OTP attempts reached. Please request a new code.");
    }

    @Test
    @DisplayName("UTCID11 [A]: validateAndConsume throws PasswordResetUnavailableException on Redis failure")
    void validateRedisFailure() {
        var cause = new DataAccessResourceFailureException("redis down");
        when(redis.execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), anyList(), anyString())).thenThrow(cause);

        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", "123456"))
                .isInstanceOf(PasswordResetUnavailableException.class)
                .hasCause(cause);
    }

    @Test
    @DisplayName("UTCID12 [A]: validateAndConsume throws PasswordResetUnavailableException on null script result")
    void validateNullScriptResult() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.VALIDATE_AND_CONSUME), anyList(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", "123456"))
                .isInstanceOf(PasswordResetUnavailableException.class);
    }

    @Test
    @DisplayName("UTCID13 [B]: validateAndConsume throws InvalidOtpException for null or blank inputs")
    void validateBlankInputs() {
        assertThatThrownBy(() -> store.validateAndConsume(null, "123456"))
                .isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", null))
                .isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> store.validateAndConsume("user@example.com", "   "))
                .isInstanceOf(InvalidOtpException.class);
        verifyNoInteractions(redis);
    }
}
