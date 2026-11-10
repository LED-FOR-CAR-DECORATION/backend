package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.UpdateCurrentProfileRequest;
import com.lumintra.backend.iam.api.response.CurrentProfileResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.repository.CustomerRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UpdateCurrentProfileUseCase {
    private final UserAccountRepository accounts;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;

    @Transactional
    public CurrentProfileResponse execute(String subject, UpdateCurrentProfileRequest request) {
        final long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (id <= 0) throw new InvalidCredentialsException("Invalid authenticated account.");
        var account = accounts.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Account not found."));
        if (account.getStatus() != AccountStatus.ACTIVE) throw new AccessDeniedException("Account is not active.");

        List<String> roles;
        if (account.getAccountType() == AccountType.CUSTOMER) {
            var profile = customers.findById(id)
                    .orElseThrow(() -> new UserNotFoundException("Customer profile not found."));
            profile.setFullName(request.name());
            profile.setPhone(request.phone());
            profile.setAvatarUrl(request.avatar());
            profile.setUpdatedAt(Instant.now());
            customers.saveAndFlush(profile);
            roles = List.of("CUSTOMER");
        } else if (account.getAccountType() == AccountType.EMPLOYEE) {
            var profile = employees.findByAccount_AccountId(id)
                    .orElseThrow(() -> new UserNotFoundException("Employee profile not found."));
            if (profile.getRole() == null || profile.getRole().getRoleName() == null || profile.getRole().getRoleName().isBlank()) {
                throw new IllegalStateException("Employee role is missing");
            }
            roles = List.of(profile.getRole().getRoleName().toUpperCase(Locale.ROOT));
            profile.setFullName(request.name());
            profile.setPhone(request.phone());
            profile.setAvatarUrl(request.avatar());
            profile.setUpdatedAt(Instant.now());
            employees.saveAndFlush(profile);
        } else {
            throw new AccessDeniedException("Unsupported account type.");
        }
        String avatar = request.avatar() == null ? account.getAvatarUrl() : request.avatar();
        return new CurrentProfileResponse(id, request.name(), account.getEmail(), request.phone(), avatar, roles);
    }
}
