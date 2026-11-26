package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.event.StaffAccountLockedEvent;
import com.lumintra.backend.iam.domain.event.StaffLoginSuccessEvent;
import com.lumintra.backend.iam.domain.event.VerificationEmailResentEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.LoginAttemptService;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.AccountNotVerifiedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.exception.iam.MaxResendAttemptsException;
import com.lumintra.backend.shared.exception.iam.ResendCooldownException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

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
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(noRollbackFor = AccountNotVerifiedException.class)
    public LoginResponse execute(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (loginAttemptService.isAccountLocked(normalizedEmail)) {
            log.warn("Login rejected: Account locked [email={}]", normalizedEmail);
            throw new AccountLockedException("Account is locked for 15 minutes.");
        }

        UserAccountEntity account = userAccountRepository.findByEmail(normalizedEmail).orElse(null);

        if (account != null && account.getStatus() == AccountStatus.DELETED) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (account == null
                || account.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            handleFailedLogin(normalizedEmail, account);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        loginAttemptService.resetAttempts(normalizedEmail);

        if (account.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            if (account.getAccountType() == AccountType.CUSTOMER) {
                dispatchVerificationEmail(normalizedEmail, account.getAccountId());
            }
            log.warn("Login rejected: Account pending verification [accountId={}] [email={}]",
                    account.getAccountId(), normalizedEmail);
            throw new AccountNotVerifiedException("Account email is not verified. Please verify your email.");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            log.warn("Login rejected: Account is not active [accountId={}] [status={}]",
                    account.getAccountId(), account.getStatus());
            throw new InvalidCredentialsException("Account is suspended or deleted.");
        }

        List<String> roles;

        // Resolve RBAC based on Account Type mapping
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            EmployeeEntity employee = employeeRepository.findByAccount_AccountId(account.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Employee profile missing for account"));

            String roleName = employee.getRole().getRoleName();
            roles = List.of("ROLE_" + roleName.toUpperCase(Locale.ROOT));

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

    private void dispatchVerificationEmail(String normalizedEmail, Long accountId) {
        try {
            String otp = otpService.generateOtpForResend(normalizedEmail, OtpPurpose.EMAIL_VERIFICATION);
            eventPublisher.publishEvent(new VerificationEmailResentEvent(normalizedEmail, otp));
            log.info("Verification email queued during pending login [accountId={}] [email={}]",
                    accountId, normalizedEmail);
        } catch (ResendCooldownException | MaxResendAttemptsException ex) {
            log.warn("Verification email skipped during pending login [accountId={}] [email={}] [reason={}]",
                    accountId, normalizedEmail, ex.getMessage());
        }
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
