package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.PasswordResetOtpStore;
import com.lumintra.backend.shared.exception.iam.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;

@Service
public class RedisPasswordResetOtpStore implements PasswordResetOtpStore {

    // First three requests in a fixed 24h window are allowed; the fourth starts a full 24h lock.
    // The lock never slides on subsequent rejected requests. Keys share a Redis Cluster slot.
    static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[3]) == 1 then return 0 end
        local count = redis.call('INCR', KEYS[2])
        if count == 1 then redis.call('EXPIRE', KEYS[2], 86400) end
        if count > 3 then
            redis.call('SET', KEYS[3], '1', 'EX', 86400)
            redis.call('DEL', KEYS[2])
            return 0
        end
        if ARGV[1] ~= '' then
            redis.call('SET', KEYS[1], ARGV[1], 'EX', 300)
            redis.call('DEL', KEYS[4])
        end
        return 1
        """, Long.class);

    // Validates OTP against stored digest and consumes it atomically.
    // Returns 1 on success, 0 on mismatch, -1 if expired/missing, -2 if max attempts reached.
    static final DefaultRedisScript<Long> VALIDATE_AND_CONSUME = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 0 then
            return -1
        end
        local stored = redis.call('GET', KEYS[1])
        if stored == ARGV[1] then
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            return 1
        else
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
                redis.call('EXPIRE', KEYS[2], 300)
            end
            if attempts >= 5 then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return -2
            end
            return 0
        end
        """, Long.class);

    private final StringRedisTemplate redis;
    private final byte[] secret;

    public RedisPasswordResetOtpStore(StringRedisTemplate redis, @Value("${app.jwt.secret}") String secret) {
        this.redis = redis;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void issue(String email, String otp) {
        String key = "auth:password-reset:{" + digest("email:" + email) + "}:";
        try {
            Long result = redis.execute(ISSUE, List.of(key + "otp", key + "requests", key + "lock", key + "verify_attempts"),
                    otp == null ? "" : digest("otp:" + email + ":" + otp));
            if (result == null) throw new PasswordResetUnavailableException();
            if (result == 0) throw new PasswordResetLockedException();
            if (result != 1) throw new PasswordResetUnavailableException();
        } catch (DataAccessException ex) {
            throw new PasswordResetUnavailableException(ex);
        }
    }

    @Override
    public boolean validateAndConsume(String email, String otp) {
        if (email == null || otp == null || otp.isBlank()) {
            throw new InvalidOtpException("Invalid or expired OTP.");
        }
        String key = "auth:password-reset:{" + digest("email:" + email) + "}:";
        try {
            Long result = redis.execute(VALIDATE_AND_CONSUME, List.of(key + "otp", key + "verify_attempts"),
                    digest("otp:" + email + ":" + otp));
            if (result == null) {
                throw new PasswordResetUnavailableException();
            }
            if (result == 1) {
                return true;
            }
            if (result == -2) {
                throw new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code.");
            }
            // result == 0 (mismatch) or result == -1 (not found / expired)
            throw new InvalidOtpException("Invalid or expired OTP.");
        } catch (DataAccessException ex) {
            throw new PasswordResetUnavailableException(ex);
        }
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new PasswordResetUnavailableException(ex);
        }
    }
}
