package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RegisterAccountService {
    private final UserAccountRepository accounts;
    private final CustomerProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse create(RegisterRequest request) {
        if (accounts.existsByEmailIgnoreCase(request.email())) throw new EmailAlreadyRegisteredException();
        var account = UserAccountEntity.registerCustomer(request.email(), passwordEncoder.encode(request.password()));
        accounts.saveAndFlush(account);
        profiles.saveAndFlush(CustomerProfileEntity.forAccount(account));
        return response(account);
    }

    @Transactional(readOnly = true)
    public void requirePending(String email) {
        var account = accounts.findByEmailIgnoreCase(email).orElseThrow(RegisterAccountService::invalidCode);
        if (!"PENDING_VERIFICATION".equals(account.getStatus())) throw invalidCode();
    }

    @Transactional
    public RegisterResponse activate(String email) {
        var account = accounts.lockByEmail(email).orElseThrow(RegisterAccountService::invalidCode);
        if (!"PENDING_VERIFICATION".equals(account.getStatus())) throw invalidCode();
        account.setStatus("ACTIVE");
        accounts.saveAndFlush(account);
        return response(account);
    }
    private static RegisterResponse response(UserAccountEntity account) {
        return new RegisterResponse(account.getAccountId(), account.getEmail(), account.getStatus());
    }
    private static EmailVerificationException invalidCode() { return new EmailVerificationException("OTP_INVALID", "The verification code is invalid or expired."); }
}
