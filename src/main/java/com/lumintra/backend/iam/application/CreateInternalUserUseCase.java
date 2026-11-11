package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.CreateInternalUserRequest;
import com.lumintra.backend.iam.api.response.CreateInternalUserResponse;
import com.lumintra.backend.iam.domain.event.InternalUserCreatedEvent;
import com.lumintra.backend.iam.domain.model.*;
import com.lumintra.backend.iam.domain.service.InternalPasswordGenerator;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.mapper.InternalUserMapper;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.iam.*;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateInternalUserUseCase {
    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final InternalPasswordGenerator passwords;
    private final InternalUserMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public CreateInternalUserResponse execute(String subject, CreateInternalUserRequest request) {
        long adminId = requireAdmin(subject);
        if (accounts.existsByEmailIgnoreCase(request.email()))
            throw new EmailAlreadyRegisteredException("Email is already registered");
        var role =
                roles.findByRoleName(request.role().name())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Configured internal role is missing"));
        String password = passwords.generate();
        var account =
                UserAccountEntity.createInternalAccount(request.email(), encoder.encode(password));
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "user_account_email_key".equals(violation.getConstraintName()))
                    throw new EmailAlreadyRegisteredException("Email is already registered", ex);
            }
            throw ex;
        }
        var employee = EmployeeEntity.createInternal(account, role, request.name());
        employees.saveAndFlush(employee);
        var response = mapper.toResponse(employee);
        events.publishEvent(
                new InternalUserCreatedEvent(
                        account.getAccountId(),
                        adminId,
                        role.getRoleName(),
                        account.getEmail(),
                        password,
                        UUID.randomUUID().toString(),
                        Instant.now()));
        return response;
    }

    private long requireAdmin(String subject) {
        final long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (id <= 0) throw new InvalidCredentialsException("Invalid authenticated account.");
        var account =
                accounts.findById(id)
                        .orElseThrow(() -> new InvalidCredentialsException("Account not found."));
        if (account.getAccountType() != AccountType.EMPLOYEE
                || account.getStatus() != AccountStatus.ACTIVE)
            throw new AccessDeniedException("Active admin account is required.");
        var employee =
                employees
                        .findByAccount_AccountId(id)
                        .orElseThrow(() -> new AccessDeniedException("Admin profile is required."));
        if (employee.getRole() == null || !"ADMIN".equals(employee.getRole().getRoleName()))
            throw new AccessDeniedException("Current admin role is required.");
        return id;
    }
}
