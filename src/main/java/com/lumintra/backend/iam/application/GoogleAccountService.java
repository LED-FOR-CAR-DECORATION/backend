package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.domain.service.GoogleIdentityVerifier.GoogleIdentity;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.AuthFlowException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {
    private final GoogleIdentityRepository identities;
    private final UserAccountRepository accounts;
    private final CustomerProfileRepository customers;
    private final PasswordEncoder encoder;

    @Transactional
    public UserAccountEntity resolve(GoogleIdentity identity, boolean management, String password) {
        var linked = identities.findById(identity.subject());
        if (linked.isPresent()) {
            var account = accounts.lockById(linked.get().getAccount().getAccountId()).orElseThrow();
            TokenSessionService.checkAccount(account, management);
            return account;
        }
        var existing = accounts.lockByEmail(identity.email());
        UserAccountEntity account;
        if (existing.isPresent()) {
            account = existing.get();
            TokenSessionService.checkAccount(account, management);
            // Google is not authoritative for third-party email ownership: require the local password to link.
            if (!identity.authoritativeEmail() && (password == null || account.getPasswordHash() == null)) {
                throw new AuthFlowException("GOOGLE_LINK_REQUIRED", "Cần mật khẩu tài khoản hiện có để liên kết email này với Google.");
            }
            if (!identity.authoritativeEmail() && !encoder.matches(password, account.getPasswordHash())) throw new InvalidCredentialsException("Invalid email or password");
            if (identities.existsByAccount_AccountId(account.getAccountId())) throw new InvalidCredentialsException("Tài khoản đã liên kết với Google identity khác.");
        } else {
            if (management) throw new AuthFlowException("MANAGEMENT_ACCESS_DENIED", "Email chưa có tài khoản nội bộ.");
            account = new UserAccountEntity();
            account.setEmail(identity.email());
            account.setAccountType(AccountType.CUSTOMER);
            account.setStatus("ACTIVE");
            accounts.saveAndFlush(account);
            var profile = new CustomerProfileEntity();
            profile.setAccount(account);
            String name = identity.name() == null ? identity.email() : identity.name().strip().replaceAll("[\\p{Cc}\\p{Cf}]", "");
            if (name.length() < 2) name = identity.email();
            profile.setFullName(name.substring(0, Math.min(name.length(), 100)));
            customers.saveAndFlush(profile);
        }
        var link = new GoogleIdentityEntity();
        link.setSubject(identity.subject()); link.setAccount(account);
        identities.saveAndFlush(link);
        return account;
    }
}
