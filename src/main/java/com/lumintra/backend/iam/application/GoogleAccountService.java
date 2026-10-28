package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.domain.service.GoogleIdentityVerifier.GoogleIdentity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class GoogleAccountService {
    private final UserAccountRepository accounts;
    private final CustomerProfileRepository customers;
    private final PasswordEncoder encoder;

    @Transactional
    public UserAccountEntity resolve(GoogleIdentity identity, boolean management, String password) {
        var existing=accounts.lockByEmail(identity.email());
        if(existing.isPresent()) {
            var account=existing.get(); TokenSessionService.checkAccount(account,management);
            if(!identity.authoritativeEmail()) {
                if(password==null || account.getPasswordHash()==null) throw linkingRequired();
                if(!encoder.matches(password,account.getPasswordHash())) throw new InvalidCredentialsException("Invalid email or password");
            }
            return account;
        }
        if(management) throw new AuthFlowException("MANAGEMENT_ACCESS_DENIED","No internal account exists for this email address.");
        // The approved schema has no provider identity table. Never auto-link an email Google does not own.
        if(!identity.authoritativeEmail()) throw linkingRequired();
        var account=UserAccountEntity.googleCustomer(identity.email()); accounts.saveAndFlush(account);
        String name=identity.name()==null?identity.email():identity.name().strip().replaceAll("[\\p{Cc}\\p{Cf}]","");
        if(name.length()<2) name=identity.email();
        customers.saveAndFlush(CustomerProfileEntity.forAccount(account,name.substring(0,Math.min(name.length(),100))));
        return account;
    }
    private static AuthFlowException linkingRequired() {
        return new AuthFlowException("GOOGLE_LINK_REQUIRED","Register and verify your email first, then provide the existing account password to link a non-Gmail/Workspace email.");
    }
}
