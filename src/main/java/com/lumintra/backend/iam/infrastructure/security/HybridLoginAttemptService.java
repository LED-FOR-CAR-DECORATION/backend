package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Duration;

/** Database is authoritative, including during Redis outages/recovery. */
@Service
@RequiredArgsConstructor
public class HybridLoginAttemptService implements LoginAttemptService {
    private final UserAccountRepository accounts;
    @Override
    @Transactional(readOnly = true)
    public boolean isAccountLocked(String email) {
        return accounts.findByEmailIgnoreCase(email).map(a -> a.getLockoutUntil() != null && a.getLockoutUntil().isAfter(Instant.now())).orElse(false);
    }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registerFailedAttempt(String email) {
        return accounts.lockByEmail(email).map(account -> {
            Instant now = Instant.now();
            if (account.getLockoutUntil() != null && account.getLockoutUntil().isAfter(now)) return 0;
            if (account.getLockoutUntil() != null) { account.setFailedAttempts(0); account.setLockoutUntil(null); }
            int count = account.getFailedAttempts() + 1;
            account.setFailedAttempts(count);
            if (count >= 5) account.setLockoutUntil(now.plus(Duration.ofMinutes(15)));
            accounts.saveAndFlush(account);
            return count;
        }).orElse(0);
    }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAttempts(String email) {
        accounts.lockByEmail(email).ifPresent(account -> {
            account.setFailedAttempts(0); account.setLockoutUntil(null); accounts.saveAndFlush(account);
        });
    }
}
