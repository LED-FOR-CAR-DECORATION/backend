package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.PermissionEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeProfileEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeProfileRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final UserAccountRepository userAccountRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public LoginResponse execute(LoginRequest request) {
        if (loginAttemptService.isAccountLocked(request.email())) {
            log.warn("Login rejected: Account locked [email={}]", request.email());
            throw new AccountLockedException("Account is locked for 15 minutes.");
        }

        UserAccountEntity account = userAccountRepository.findByEmail(request.email()).orElse(null);

        if (account == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            handleFailedLogin(request.email(), account);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        loginAttemptService.resetAttempts(request.email());

        List<String> roles;
        Set<String> permissions;

        // Resolve RBAC based on Account Type mapping
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            EmployeeProfileEntity employee = employeeProfileRepository.findByAccount_AccountId(account.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Employee profile missing for account"));

            String roleName = employee.getRole().getRoleName();
            roles = List.of("ROLE_" + roleName.toUpperCase());
            permissions = employee.getRole().getPermissions().stream()
                    .map(PermissionEntity::getPermissionName)
                    .collect(Collectors.toSet());

            eventPublisher.publishEvent(new StaffLoginSuccessEvent(account.getAccountId(), account.getEmail(), roleName));
        } else {
            roles = List.of("ROLE_CUSTOMER");
            permissions = Set.of(); // Customers implicitly do not have backend dashboard permissions
        }

        String accessToken = tokenProvider.createAccessToken(account.getAccountId(), account.getEmail(), roles, permissions);
        String refreshToken = tokenProvider.createRefreshToken(account.getAccountId());

        log.info("User login successful [accountId={}] [accountType={}]", account.getAccountId(), account.getAccountType());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .build();
    }

    private void handleFailedLogin(String email, UserAccountEntity account) {
        int attempts = loginAttemptService.registerFailedAttempt(email);
        log.warn("Login failed [email={}] [attemptCount={}]", email, attempts);

        if (attempts >= 5) {
            log.warn("Account lockout triggered [email={}]", email);
            if (account != null && account.getAccountType() == AccountType.EMPLOYEE) {
                employeeProfileRepository.findByAccount_AccountId(account.getAccountId()).ifPresent(emp ->
                        eventPublisher.publishEvent(new StaffAccountLockedEvent(account.getAccountId(), account.getEmail(), emp.getRole().getRoleName()))
                );
            }
        }
    }
}
