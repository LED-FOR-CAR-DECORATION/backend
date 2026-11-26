package com.lumintra.backend.iam.application;
import com.lumintra.backend.iam.api.request.ForgotPasswordRequest;
import com.lumintra.backend.iam.api.response.ForgotPasswordResponse;
import com.lumintra.backend.iam.domain.event.PasswordResetRequestedEvent;
import com.lumintra.backend.iam.domain.service.PasswordResetOtpStore;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ForgotPasswordUseCase {
    private final UserAccountRepository accounts;
    private final PasswordResetOtpStore otpStore;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();
    public ForgotPasswordResponse execute(ForgotPasswordRequest request) {
        String email = request.email();
        boolean exists = accounts.existsByEmailIgnoreCase(email);
        String otp = exists ? String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)) : null;
        otpStore.issue(email, otp);
        if (exists) events.publishEvent(new PasswordResetRequestedEvent(email, otp, UUID.randomUUID().toString()));
        return new ForgotPasswordResponse("If the email is registered, a password reset code has been sent.");
    }
}
