package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.EmailVerificationStore;
import com.lumintra.backend.shared.exception.iam.EmailVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class RedisEmailVerificationStore implements EmailVerificationStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[3]) == 1 then return 0 end
        redis.call('SET', KEYS[1], ARGV[1], 'EX', 300)
        redis.call('DEL', KEYS[2])
        redis.call('SET', KEYS[3], '1', 'EX', 60)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        local digest = redis.call('GET', KEYS[1])
        if not digest then return 0 end
        if digest == ARGV[1] then redis.call('DEL', KEYS[1], KEYS[2]); return 1 end
        local attempts = redis.call('INCR', KEYS[2])
        if attempts == 1 then redis.call('EXPIRE', KEYS[2], 300) end
        if attempts >= 5 then redis.call('DEL', KEYS[1], KEYS[2]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1], KEYS[2], KEYS[3]) end
        return 0
        """, Long.class);
    public void issue(String email, String digest) {
        if (!Long.valueOf(1).equals(execute(ISSUE, email, digest))) {
            throw new EmailVerificationException("OTP_RATE_LIMITED", "Please wait 60 seconds before requesting another code.");
        }
    }
    public boolean consume(String email, String digest) { return Long.valueOf(1).equals(execute(CONSUME, email, digest)); }
    public void invalidate(String email, String digest) { execute(INVALIDATE, email, digest); }
    private Long execute(DefaultRedisScript<Long> script, String email, String digest) {
        try {
            String tag = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)));
            String prefix = "auth:registration:{" + tag + "}:";
            Long result = redis.execute(script, List.of(prefix + "otp", prefix + "attempts", prefix + "cooldown"), digest);
            if (result == null) throw new IllegalStateException("No Redis script result");
            return result;
        } catch (GeneralSecurityException | org.springframework.dao.DataAccessException | IllegalStateException ex) {
            throw new EmailVerificationException("VERIFICATION_UNAVAILABLE", "Email verification is temporarily unavailable.", ex);
        }
    }
}
