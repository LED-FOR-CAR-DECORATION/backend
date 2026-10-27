package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.event.*;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUseCase {
    private final UserAccountRepository accounts;
    private final EmployeeProfileRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService sessions;
    private final LoginAttemptService attempts;
    private final ApplicationEventPublisher events;

    public LoginResponse execute(LoginRequest request) { return execute(request, false); }
    public LoginResponse execute(LoginRequest request, boolean management) {
        if (attempts.isAccountLocked(request.email())) throw new AccountLockedException("Account is locked for 15 minutes.");
        var account = accounts.findByEmailIgnoreCase(request.email()).orElse(null);
        if (account == null || account.getPasswordHash() == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            recordFailedAttempt(request.email());
            throw new InvalidCredentialsException("Invalid email or password");
        }
        TokenSessionService.checkAccount(account, management);
        LoginResponse response = sessions.issue(account.getAccountId(), management);
        attempts.resetAttempts(request.email());
        auditSuccess(account);
        return response;
    }
    public void recordFailedAttempt(String email) {
        int count = attempts.registerFailedAttempt(email);
        if (count == 5) accounts.findByEmailIgnoreCase(email).filter(account -> account.getAccountType() == AccountType.EMPLOYEE)
                .ifPresent(account -> employees.findByAccount_AccountId(account.getAccountId()).ifPresent(employee ->
                        events.publishEvent(new StaffAccountLockedEvent(account.getAccountId(), account.getEmail(), employee.getRole().getRoleName()))));
    }
    public void auditSuccess(UserAccountEntity account) {
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            employees.findByAccount_AccountId(account.getAccountId()).ifPresent(employee ->
                    events.publishEvent(new StaffLoginSuccessEvent(account.getAccountId(), account.getEmail(), employee.getRole().getRoleName())));
        }
    }
}
