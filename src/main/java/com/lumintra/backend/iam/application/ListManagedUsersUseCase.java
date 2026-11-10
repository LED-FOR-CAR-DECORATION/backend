package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.ManagedUserFilterRequest;
import com.lumintra.backend.iam.api.response.ManagedUserListResponse;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.ManagedUserMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.ManagedUserSpecifications;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.PaginationMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListManagedUsersUseCase {
    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final ManagedUserMapper mapper;

    @Transactional(readOnly = true)
    public ManagedUserListResponse execute(String subject, ManagedUserFilterRequest filter) {
        final long accountId;
        try { accountId = Long.parseLong(subject); }
        catch (NumberFormatException ex) { throw new InvalidCredentialsException("Invalid authenticated account."); }
        if (accountId <= 0) throw new InvalidCredentialsException("Invalid authenticated account.");
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new InvalidCredentialsException("Account not found."));
        if (account.getStatus() != AccountStatus.ACTIVE || account.getAccountType() != AccountType.EMPLOYEE) {
            throw new AccessDeniedException("Active internal account is required.");
        }
        var requester = employees.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new AccessDeniedException("Internal profile is required."));
        String role = requester.getRole() == null ? null : requester.getRole().getRoleName();
        if (role == null || !Set.of("ADMIN", "MANAGER").contains(role.toUpperCase(Locale.ROOT))) {
            throw new AccessDeniedException("Admin or manager role is required.");
        }
        var page = employees.findAll(ManagedUserSpecifications.matching(filter.role(), filter.active(), filter.search()),
                PageRequest.of(filter.page(), filter.size(), Sort.by(Sort.Direction.DESC, "account.accountId")));
        return new ManagedUserListResponse(page.getContent().stream().map(mapper::toItem).toList(), PaginationMeta.from(page));
    }
}
