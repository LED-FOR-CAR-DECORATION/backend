package com.lumintra.backend.iam.infrastructure.persistence;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InternalEmployeeAccessServiceImpl implements InternalEmployeeAccessService {

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public Long requireActiveEmployeeAccountId(String subject, Set<String> allowedRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            throw new IllegalArgumentException("allowedRoles must not be empty.");
        }

        final long accountId;
        try {
            accountId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (accountId <= 0) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }

        var account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidCredentialsException("Account not found."));

        if (account.getStatus() != AccountStatus.ACTIVE
                || account.getAccountType() != AccountType.EMPLOYEE) {
            throw new AccessDeniedException("Active internal account is required.");
        }

        var employee = employeeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new AccessDeniedException("Internal profile is required."));

        String roleName = employee.getRole() == null ? null : employee.getRole().getRoleName();
        Set<String> normalizedAllowedRoles = allowedRoles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (roleName == null || !normalizedAllowedRoles.contains(roleName.toUpperCase(Locale.ROOT))) {
            throw new AccessDeniedException("Authorized internal role is required.");
        }

        return accountId;
    }
}
