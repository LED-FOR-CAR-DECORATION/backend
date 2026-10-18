package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String ATTEMPTS_PREFIX = "auth:attempts:";
    private static final String LOCKOUT_PREFIX = "auth:lockout:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final SafeCacheService cacheService;

    @Override
    public boolean isAccountLocked(String email) {
        return cacheService.hasKey(LOCKOUT_PREFIX + email);
    }

    @Override
    public int registerFailedAttempt(String email) {
        int attempts = cacheService.increment(ATTEMPTS_PREFIX + email, LOCKOUT_DURATION);

        if (attempts >= MAX_ATTEMPTS) {
            cacheService.set(LOCKOUT_PREFIX + email, "LOCKED", LOCKOUT_DURATION);
            cacheService.delete(ATTEMPTS_PREFIX + email);
            return MAX_ATTEMPTS;
        }
        return attempts;
    }

    @Override
    public void resetAttempts(String email) {
        cacheService.delete(ATTEMPTS_PREFIX + email);
        cacheService.delete(LOCKOUT_PREFIX + email);
    }
}