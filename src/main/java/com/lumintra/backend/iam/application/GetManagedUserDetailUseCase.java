package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.response.ManagedUserDetailResponse;
import com.lumintra.backend.iam.domain.event.UserDetailViewedEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.ManagedUserDetailMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetManagedUserDetailUseCase {
    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final ManagedUserDetailMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public ManagedUserDetailResponse execute(String subject, long targetId) {
        var viewer = requireViewer(subject);
        long viewerId = viewer.getAccount().getAccountId();
        var target = viewerId == targetId ? viewer : findTarget(targetId);
        Instant snapshotTime = Instant.now();
        var response = mapper.toResponse(target, snapshotTime);
        if (viewerId != targetId) {
            events.publishEvent(
                    new UserDetailViewedEvent(
                            viewerId,
                            targetId,
                            viewer.getRole().getRoleName().toUpperCase(Locale.ROOT),
                            snapshotTime));
        }
        return response;
    }

    private EmployeeEntity requireViewer(String subject) {
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
            throw new AccessDeniedException("Active internal account is required.");
        }
        var viewer =
                employees
                        .findByAccount_AccountId(id)
                        .orElseThrow(
                                () -> new AccessDeniedException("Internal profile is required."));
        String role = viewer.getRole() == null ? null : viewer.getRole().getRoleName();
        if (role == null || !Set.of("ADMIN", "MANAGER").contains(role.toUpperCase(Locale.ROOT))) {
            throw new AccessDeniedException("Admin or manager role is required.");
        }
        return viewer;
    }

    private EmployeeEntity findTarget(long id) {
        var account =
                accounts.findById(id)
                        .orElseThrow(() -> new UserNotFoundException("User not found."));
        if (account.getAccountType() != AccountType.EMPLOYEE) {
            throw new AccessDeniedException("Customer user details are not available.");
        }
        var target =
                employees
                        .findByAccount_AccountId(id)
                        .orElseThrow(
                                () -> new UserNotFoundException("Employee profile not found."));
        if (target.getRole() == null
                || target.getRole().getRoleName() == null
                || target.getRole().getRoleName().isBlank()) {
            throw new IllegalStateException("Employee role is missing");
        }
        if ("CUSTOMER".equalsIgnoreCase(target.getRole().getRoleName())) {
            throw new AccessDeniedException("Customer user details are not available.");
        }
        return target;
    }
}
