package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerProfileEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerProfileRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.EmailAlreadyRegisteredException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUseCase {
    private final UserAccountRepository accounts;
    private final CustomerProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse execute(RegisterRequest request) {
        if (accounts.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }
        UserAccountEntity account = new UserAccountEntity();
        account.setEmail(request.email());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setAccountType(AccountType.CUSTOMER);
        account.setStatus("ACTIVE");
        UserAccountEntity saved = accounts.saveAndFlush(account);

        CustomerProfileEntity profile = new CustomerProfileEntity();
        profile.setAccount(saved);
        profile.setFullName(request.fullName());
        profiles.saveAndFlush(profile);
        return new RegisterResponse(saved.getAccountId(), saved.getEmail(), profile.getFullName());
    }
}
