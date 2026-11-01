package com.lumintra.backend.iam.application;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AccountLookup {
    private final UserAccountRepository accounts;
    public boolean isActive(Long id) {
        return accounts.findById(id).map(a -> "ACTIVE".equals(a.getStatus())).orElse(false);
    }
}
