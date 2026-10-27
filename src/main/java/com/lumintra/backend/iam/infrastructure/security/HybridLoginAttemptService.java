package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class HybridLoginAttemptService implements LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(HybridLoginAttemptService.class);
    private static final String ATTEMPTS_PREFIX = "auth:attempts:";
    private static final String LOCKOUT_PREFIX = "auth:lockout:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final SafeCacheService cacheService;
    private final UserAccountRepository userAccountRepository;

    @Override
    @Transactional
    public boolean isAccountLocked(String email) {
        try {
            return cacheService.hasKey(LOCKOUT_PREFIX + email);
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, checking DB for lockout [email={}]", email);
            return userAccountRepository.findByEmail(email)
                    .map(user -> user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now()))
                    .orElse(false);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registerFailedAttempt(String email) {
        try {
            int attempts = cacheService.increment(ATTEMPTS_PREFIX + email, LOCKOUT_DURATION);
            if (attempts >= MAX_ATTEMPTS) {
                cacheService.set(LOCKOUT_PREFIX + email, "LOCKED", LOCKOUT_DURATION);
                cacheService.delete(ATTEMPTS_PREFIX + email);
                return MAX_ATTEMPTS;
            }
            return attempts;
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, registering failed attempt in DB [email={}]", email);
            return userAccountRepository.findByEmail(email).map(user -> {
                user.setFailedAttempts(user.getFailedAttempts() + 1);
                if (user.getFailedAttempts() >= MAX_ATTEMPTS) {
                    user.setLockoutUntil(Instant.now().plus(LOCKOUT_DURATION));
                }
                userAccountRepository.save(user);
                return user.getFailedAttempts();
            }).orElse(0);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAttempts(String email) {
        try {
            cacheService.delete(ATTEMPTS_PREFIX + email);
            cacheService.delete(LOCKOUT_PREFIX + email);
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable, resetting attempts in DB [email={}]", email);
            userAccountRepository.findByEmail(email).ifPresent(user -> {
                user.setFailedAttempts(0);
                user.setLockoutUntil(null);
                userAccountRepository.save(user);
            });
        }
    }
}