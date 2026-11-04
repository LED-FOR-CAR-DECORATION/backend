package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.VerifyEmailRequest;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.AccountAlreadyVerifiedException;
import com.lumintra.backend.shared.exception.iam.InvalidOtpException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerifyEmailUseCase.class);
    private final UserAccountRepository userAccountRepository;
    private final OtpService otpService;

    @Transactional
    public void execute(VerifyEmailRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        UserAccountEntity account = userAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("Account not found"));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new AccountAlreadyVerifiedException("Account is already verified");
        }

        boolean isValid = otpService.validateOtp(normalizedEmail, request.otp(), OtpPurpose.EMAIL_VERIFICATION);
        if (!isValid) {
            throw new InvalidOtpException("Invalid or expired OTP.");
        }

        account.verifyEmail();
        userAccountRepository.save(account);
        log.info("Email verified successfully. Account is now ACTIVE [email={}]", normalizedEmail);
    }
}