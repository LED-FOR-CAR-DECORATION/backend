package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.response.CurrentProfileResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GetCurrentProfileUseCase {
    private final UserAccountRepository accounts;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public CurrentProfileResponse execute(String subject) {
        final long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (id <= 0) throw new InvalidCredentialsException("Invalid authenticated account.");
        var account =
                accounts.findById(id)
                        .orElseThrow(() -> new UserNotFoundException("Account not found."));
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new AccessDeniedException("Account is not active.");
        String name;
        String phone;
        String avatar;
        List<String> roles;
        if (account.getAccountType() == AccountType.CUSTOMER) {
            var profile =
                    customers
                            .findById(id)
                            .orElseThrow(
                                    () -> new UserNotFoundException("Customer profile not found."));
            name = profile.getFullName();
            phone = profile.getPhone();
            avatar = profile.getAvatarUrl();
            roles = List.of("CUSTOMER");
        } else if (account.getAccountType() == AccountType.EMPLOYEE) {
            var profile =
                    employees
                            .findByAccount_AccountId(id)
                            .orElseThrow(
                                    () -> new UserNotFoundException("Employee profile not found."));
            if (profile.getRole() == null
                    || profile.getRole().getRoleName() == null
                    || profile.getRole().getRoleName().isBlank()) {
                throw new IllegalStateException("Employee role is missing");
            }
            name = profile.getFullName();
            phone = profile.getPhone();
            avatar = profile.getAvatarUrl();
            roles = List.of(profile.getRole().getRoleName().toUpperCase(Locale.ROOT));
        } else {
            throw new AccessDeniedException("Unsupported account type.");
        }
        if (avatar == null || avatar.isBlank()) avatar = account.getAvatarUrl();
        return new CurrentProfileResponse(id, name, account.getEmail(), phone, avatar, roles);
    }
}
