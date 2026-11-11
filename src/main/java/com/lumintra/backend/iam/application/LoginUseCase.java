package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.HybridRefreshTokenService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
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

        // Resolve RBAC based on Account Type mapping
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            EmployeeEntity employee = employeeRepository.findByAccount_AccountId(account.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Employee profile missing for account"));

            String roleName = employee.getRole().getRoleName();
            roles = List.of("ROLE_" + roleName.toUpperCase());

            eventPublisher.publishEvent(new StaffLoginSuccessEvent(account.getAccountId(), account.getEmail(), roleName));
        } else {
            roles = List.of("ROLE_CUSTOMER");
        }

        String accessToken = tokenProvider.createAccessToken(account.getAccountId(), account.getEmail(), roles);
        String refreshToken = tokenProvider.createRefreshToken(account.getAccountId());

        refreshTokenService.storeRefreshToken(account.getAccountId(), refreshToken);

        log.info("User login successful [accountId={}] [accountType={}]", account.getAccountId(), account.getAccountType());

        return LoginResponse.of(accessToken, refreshToken);
    }

    private void handleFailedLogin(String email, UserAccountEntity account) {
        int attempts = loginAttemptService.registerFailedAttempt(email);
        log.warn("Login failed [email={}] [attemptCount={}]", email, attempts);

        if (attempts >= 5) {
            log.warn("Account lockout triggered [email={}]", email);
            if (account != null && account.getAccountType() == AccountType.EMPLOYEE) {
                employeeRepository.findByAccount_AccountId(account.getAccountId()).ifPresent(emp ->
                        eventPublisher.publishEvent(new StaffAccountLockedEvent(account.getAccountId(), account.getEmail(), emp.getRole().getRoleName()))
                );
            }
        }
    }
}
