package com.lumintra.backend.iam;

import com.lumintra.backend.iam.infrastructure.security.RedisEmailVerificationStore;
import com.lumintra.backend.shared.exception.iam.EmailVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisEmailVerificationStoreTest {
    @Test void storageKeysDoNotExposeEmailAndCodeIsStoredAsDigest() {
        var redis=mock(StringRedisTemplate.class);
        doAnswer(call -> {
            List<String> keys=call.getArgument(1);
            assertThat(keys).hasSize(3).allMatch(key->key.startsWith("auth:registration:{") && !key.contains("@"));
            assertThat((Object)call.getArgument(2)).isEqualTo("test-hmac-digest");
            return 1L;
        }).when(redis).execute(any(DefaultRedisScript.class),anyList(),anyString());
        new RedisEmailVerificationStore(redis).issue("customer@example.com","test-hmac-digest");
    }
    @Test void cooldownAndExpiredCodeAreNotTreatedAsSuccess() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class),anyList(),anyString())).thenReturn(0L);
        var store=new RedisEmailVerificationStore(redis);
        assertThatThrownBy(()->store.issue("customer@example.com","digest")).isInstanceOfSatisfying(EmailVerificationException.class,e->assertThat(e.getCode()).isEqualTo("OTP_RATE_LIMITED"));
        assertThat(store.consume("customer@example.com","digest")).isFalse();
    }
    @Test void redisOutageReturnsAServiceErrorRatherThanSuccessfulVerification() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class),anyList(),anyString())).thenThrow(new DataAccessResourceFailureException("test outage"));
        var store=new RedisEmailVerificationStore(redis);
        assertThatThrownBy(()->store.consume("customer@example.com","digest")).isInstanceOfSatisfying(EmailVerificationException.class,e->assertThat(e.getCode()).isEqualTo("VERIFICATION_UNAVAILABLE"));
    }
}
