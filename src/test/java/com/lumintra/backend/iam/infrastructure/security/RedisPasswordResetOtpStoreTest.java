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
    @Mock StringRedisTemplate redis;
    RedisPasswordResetOtpStore store;
    @BeforeEach void setup() { store = new RedisPasswordResetOtpStore(redis, "test-secret-only-not-a-real-key"); }
    @Test @DisplayName("N: Store protected OTP and namespaced keys in one atomic script")
    @SuppressWarnings("unchecked") void protectedChallenge() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenReturn(1L);
        store.issue("customer@example.com", "001234");
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(eq(RedisPasswordResetOtpStore.ISSUE), keys.capture(), digest.capture());
        assertThat(keys.getValue()).hasSize(3).allSatisfy(k -> assertThat(k).startsWith("auth:password-reset:{").doesNotContain("customer@example.com"));
        assertThat(digest.getValue()).matches("[a-f0-9]{64}").doesNotContain("001234");
    }
    @Test @DisplayName("N: Unknown email counts toward limits without storing a challenge")
    void unknown() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), eq(""))).thenReturn(1L);
        store.issue("missing@example.com", null);
        verify(redis).execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), eq(""));
    }
    @Test @DisplayName("B: Redis denial maps to a reset lock") void locked() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenReturn(0L);
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetLockedException.class);
    }
    @Test @DisplayName("A: Redis failure is fail-closed and preserves cause") void outage() {
        var cause = new DataAccessResourceFailureException("offline");
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenThrow(cause);
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetUnavailableException.class).hasCause(cause);
    }
    @Test @DisplayName("A: Missing script result is not success") void nullResult() {
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetUnavailableException.class);
    }
    @Test @DisplayName("A: Unexpected script result is not success") void unexpectedResult() {
        when(redis.execute(eq(RedisPasswordResetOtpStore.ISSUE), anyList(), anyString())).thenReturn(2L);
        assertThatThrownBy(() -> store.issue("a@b.co", "123456")).isInstanceOf(PasswordResetUnavailableException.class);
    }
}
