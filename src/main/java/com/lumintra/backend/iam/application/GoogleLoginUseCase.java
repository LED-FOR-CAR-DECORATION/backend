package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.GoogleLoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.service.*;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleLoginUseCase {
    private final GoogleIdentityVerifier verifier;
    private final GoogleAccountService accounts;
    private final TokenSessionService sessions;
    private final LoginAttemptService attempts;
    private final LoginUseCase login;

    public LoginResponse execute(GoogleLoginRequest request, boolean management) {
        // External verification finishes before a database transaction is started.
        var identity = verifier.verify(request.idToken());
        if (attempts.isAccountLocked(identity.email())) throw new AccountLockedException("Account is locked for 15 minutes.");
        com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity account;
        try { account = accounts.resolve(identity, management, request.password()); }
        catch (com.lumintra.backend.shared.exception.iam.InvalidCredentialsException ex) {
            login.recordFailedAttempt(identity.email()); throw ex;
        }
        var response = sessions.issue(account.getAccountId(), management);
        attempts.resetAttempts(account.getEmail());
        login.auditSuccess(account);
        return response;
    }
}
