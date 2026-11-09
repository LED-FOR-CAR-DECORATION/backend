package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.ResendVerificationRequest;
import com.lumintra.backend.iam.domain.event.VerificationEmailResentEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.AccountAlreadyVerifiedException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendVerificationEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResendVerificationEmailUseCase.class);
    private final UserAccountRepository userAccountRepository;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void execute(ResendVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        UserAccountEntity account = userAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("Account not found"));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new AccountAlreadyVerifiedException("Account is already verified.");
        }

        String otp = otpService.generateOtpForResend(normalizedEmail, OtpPurpose.EMAIL_VERIFICATION);

        log.info("Verification email resent successfully [email={}]", normalizedEmail);
        eventPublisher.publishEvent(new VerificationEmailResentEvent(normalizedEmail, otp));
    }
}