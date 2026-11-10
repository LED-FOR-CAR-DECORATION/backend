package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.ResetPasswordRequest;
import com.lumintra.backend.iam.api.response.ResetPasswordResponse;
import com.lumintra.backend.iam.domain.event.StaffPasswordChangedEvent;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.domain.service.PasswordResetOtpStore;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.EmployeeRepository;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetPasswordUseCase.class);

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetOtpStore otpStore;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ResetPasswordResponse execute(ResetPasswordRequest request) {
        String email = request.email();

        if (request.confirmPassword() != null && !request.confirmPassword().isBlank()
                && !request.confirmPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // AC01: Validate OTP from Redis
        otpStore.validateAndConsume(email, request.otp());

        // Locate account
        UserAccountEntity account = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Account not found."));

        // AC02: Update and hash new password, clearing any temporary lockout
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setFailedAttempts(0);
        account.setLockoutUntil(null);
        account.setUpdatedAt(Instant.now());
        userAccountRepository.save(account);

        // AC03: Revoke all existing sessions
        refreshTokenService.revokeAllTokensForAccount(account.getAccountId());

        // AC04: If internal staff change password, publish audit event
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            String roleName = employeeRepository.findByAccount_AccountId(account.getAccountId())
                    .map(EmployeeEntity::getRole)
                    .map(role -> role.getRoleName())
                    .orElse("STAFF");
            eventPublisher.publishEvent(new StaffPasswordChangedEvent(account.getAccountId(), account.getEmail(), roleName));
            log.info("Staff password changed event published [accountId={}] [role={}]", account.getAccountId(), roleName);
        }

        log.info("Password successfully reset for account [accountId={}]", account.getAccountId());
        return new ResetPasswordResponse("Password has been reset successfully. Please log in with your new password.");
    }
}
