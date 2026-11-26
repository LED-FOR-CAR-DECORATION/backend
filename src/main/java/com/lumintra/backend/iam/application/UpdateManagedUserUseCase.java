package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.UpdateManagedUserRequest;
import com.lumintra.backend.iam.domain.event.ManagedUserUpdatedEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.RoleRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.InvalidManagedUserRoleException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Objects;

@Service
public class UpdateManagedUserUseCase {
    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final String defaultAdminEmail;

    public UpdateManagedUserUseCase(
            UserAccountRepository accounts,
            EmployeeRepository employees,
            RoleRepository roles,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher events,
            @Value("${app.seed.admin-email}") String defaultAdminEmail) {
        this.accounts = accounts;
        this.employees = employees;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.defaultAdminEmail = defaultAdminEmail.strip();
    }

    @Transactional
    public void execute(String subject, long targetId, UpdateManagedUserRequest request) {
        long actorId = requireAdmin(subject);
        if (actorId == targetId) {
            throw new AccessDeniedException(
                    "Use the current profile endpoint to edit your own profile.");
        }
        var target =
                accounts.findForManagementUpdate(targetId)
                        .orElseThrow(() -> new UserNotFoundException("User not found."));
        if (target.getAccountType() != AccountType.EMPLOYEE
                || target.getStatus() == AccountStatus.DELETED) {
            throw new AccessDeniedException("Only non-deleted internal accounts can be edited.");
        }
        var employee =
                employees
                        .findByAccount_AccountId(targetId)
                        .orElseThrow(
                                () -> new UserNotFoundException("Employee profile not found."));
        if (employee.getRole() == null
                || "CUSTOMER".equalsIgnoreCase(employee.getRole().getRoleName())) {
            throw new AccessDeniedException("Only internal users can be edited.");
        }
        if (defaultAdminEmail.equalsIgnoreCase(target.getEmail())
                && (request.role() != null || request.locked() != null)) {
            throw new AccessDeniedException(
                    "The default administrator's role and status are protected.");
        }

        var changed = new LinkedHashSet<String>();
        if (request.role() != null) {
            if ("CUSTOMER".equals(request.role())) {
                throw new InvalidManagedUserRoleException();
            }
            var role =
                    roles.findByRoleName(request.role())
                            .orElseThrow(InvalidManagedUserRoleException::new);
            if (!Objects.equals(employee.getRole().getRoleName(), role.getRoleName())) {
                employee.setRole(role);
                changed.add("role");
            }
        }
        if (request.locked() != null) {
            if (target.getStatus() != AccountStatus.ACTIVE
                    && target.getStatus() != AccountStatus.SUSPENDED) {
                throw new AccessDeniedException(
                        "Only active or suspended accounts can change manual lock state.");
            }
            var status = request.locked() ? AccountStatus.SUSPENDED : AccountStatus.ACTIVE;
            if (target.getStatus() != status) {
                target.setStatus(status);
                changed.add("locked");
            }
        }
        if (request.name() != null && !Objects.equals(employee.getFullName(), request.name())) {
            employee.setFullName(request.name());
            changed.add("name");
        }
        if (request.phone() != null && !Objects.equals(employee.getPhone(), request.phone())) {
            employee.setPhone(request.phone());
            changed.add("phone");
        }
        if (request.avatar() != null
                && !Objects.equals(employee.getAvatarUrl(), request.avatar())) {
            employee.setAvatarUrl(request.avatar());
            changed.add("avatar");
        }
        if (changed.isEmpty()) {
            return;
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (target.getUpdatedAt() != null && !now.isAfter(target.getUpdatedAt())) {
            now = target.getUpdatedAt().plus(1, ChronoUnit.MICROS);
        }
        target.setUpdatedAt(now);
        employee.setUpdatedAt(now);
        refreshTokens.revokeAllTokensForAccount(targetId);
        events.publishEvent(new ManagedUserUpdatedEvent(actorId, targetId, changed, now));
    }

    private long requireAdmin(String subject) {
        final long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (id <= 0) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        var account =
                accounts.findById(id)
                        .orElseThrow(() -> new InvalidCredentialsException("Account not found."));
        if (account.getAccountType() != AccountType.EMPLOYEE
                || account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active administrator is required.");
        }
        var employee =
                employees
                        .findByAccount_AccountId(id)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Administrator profile is required."));
        if (employee.getRole() == null || !"ADMIN".equals(employee.getRole().getRoleName())) {
            throw new AccessDeniedException("Current administrator role is required.");
        }
        return id;
    }
}
