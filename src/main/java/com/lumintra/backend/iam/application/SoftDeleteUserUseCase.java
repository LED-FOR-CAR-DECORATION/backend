package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.domain.event.UserSoftDeletedEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.model.UserDeletionReason;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SoftDeleteUserUseCase {
    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final String defaultAdminEmail;

    public SoftDeleteUserUseCase(
            UserAccountRepository accounts,
            EmployeeRepository employees,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher events,
            @Value("${app.seed.admin-email}") String defaultAdminEmail) {
        this.accounts = accounts;
        this.employees = employees;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.defaultAdminEmail = defaultAdminEmail.strip();
    }

    @Transactional
    public void execute(String subject, long targetId, UserDeletionReason reason) {
        long actorId = requireAdmin(subject);
        if (actorId == targetId) {
            throw new AccessDeniedException("An administrator cannot delete their own account.");
        }

        var target =
                accounts.findForDeletion(targetId)
                        .orElseThrow(() -> new UserNotFoundException("User not found."));
        if (defaultAdminEmail.equalsIgnoreCase(target.getEmail())) {
            throw new AccessDeniedException("The default administrator account is protected.");
        }
        if (target.getStatus() == AccountStatus.DELETED) {
            return;
        }

        Instant now = Instant.now();
        target.softDelete(now);
        refreshTokens.revokeAllTokensForAccount(targetId);
        events.publishEvent(new UserSoftDeletedEvent(actorId, targetId, reason, now));
    }

    private long requireAdmin(String subject) {
        final long accountId;
        try {
            accountId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (accountId <= 0) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        var account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new InvalidCredentialsException("Account not found."));
        if (account.getStatus() != AccountStatus.ACTIVE
                || account.getAccountType() != AccountType.EMPLOYEE) {
            throw new AccessDeniedException("An active administrator account is required.");
        }
        var employee =
                employees
                        .findByAccount_AccountId(accountId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Administrator profile is required."));
        if (employee.getRole() == null || !"ADMIN".equals(employee.getRole().getRoleName())) {
            throw new AccessDeniedException("Current administrator role is required.");
        }
        return accountId;
    }
}
