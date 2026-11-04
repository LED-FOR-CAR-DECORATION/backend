package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.ResendVerificationRequest;
import com.lumintra.backend.iam.domain.event.VerificationEmailResentEvent;
import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.OtpPurpose;
import com.lumintra.backend.iam.domain.service.OtpService;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.AccountAlreadyVerifiedException;
import com.lumintra.backend.shared.exception.iam.MaxResendAttemptsException;
import com.lumintra.backend.shared.exception.iam.ResendCooldownException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendVerificationEmailUseCaseTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ResendVerificationEmailUseCase resendVerificationEmailUseCase;

    @Test
    @DisplayName("UTCID01 [N]: Re-generate OTP and publish VerificationEmailResentEvent for valid PENDING account")
    void execute_validPendingAccount_generatesOtpAndPublishesEvent() {
        String email = "customer@example.com";
        ResendVerificationRequest request = new ResendVerificationRequest(email);
        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hashed_pw");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.generateOtpForResend(email, OtpPurpose.EMAIL_VERIFICATION)).thenReturn("654321");

        resendVerificationEmailUseCase.execute(request);

        verify(otpService).generateOtpForResend(email, OtpPurpose.EMAIL_VERIFICATION);
        ArgumentCaptor<VerificationEmailResentEvent> eventCaptor = ArgumentCaptor.forClass(VerificationEmailResentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        VerificationEmailResentEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.email()).isEqualTo(email);
        assertThat(publishedEvent.otp()).isEqualTo("654321");
    }

    @Test
    @DisplayName("UTCID02 [N]: Normalizes email with whitespace and uppercase characters")
    void execute_normalizesWhitespaceAndUppercaseEmail() {
        String rawEmail = "  Customer.Resend@Example.COM  ";
        String normalizedEmail = "customer.resend@example.com";
        ResendVerificationRequest request = new ResendVerificationRequest(rawEmail);
        UserAccountEntity account = UserAccountEntity.createCustomerAccount(normalizedEmail, "hashed_pw");

        when(userAccountRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(account));
        when(otpService.generateOtpForResend(normalizedEmail, OtpPurpose.EMAIL_VERIFICATION)).thenReturn("123456");

        resendVerificationEmailUseCase.execute(request);

        verify(userAccountRepository).findByEmail(normalizedEmail);
        verify(otpService).generateOtpForResend(normalizedEmail, OtpPurpose.EMAIL_VERIFICATION);
        ArgumentCaptor<VerificationEmailResentEvent> eventCaptor = ArgumentCaptor.forClass(VerificationEmailResentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo(normalizedEmail);
    }

    @Test
    @DisplayName("UTCID03 [B]: Boundary short email address format ('a@b.co')")
    void execute_boundaryEmailAddressFormat() {
        String email = "a@b.co";
        ResendVerificationRequest request = new ResendVerificationRequest(email);
        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hashed_pw");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.generateOtpForResend(email, OtpPurpose.EMAIL_VERIFICATION)).thenReturn("000123");

        resendVerificationEmailUseCase.execute(request);

        ArgumentCaptor<VerificationEmailResentEvent> eventCaptor = ArgumentCaptor.forClass(VerificationEmailResentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().otp()).isEqualTo("000123");
    }

    @Test
    @DisplayName("UTCID04 [A]: Reject resend when user account is not found in repository (404)")
    void execute_accountNotFound_throwsUserNotFoundException() {
        String email = "notfound@example.com";
        ResendVerificationRequest request = new ResendVerificationRequest(email);

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resendVerificationEmailUseCase.execute(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Account not found");

        verifyNoInteractions(otpService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("UTCID05 [A]: Reject resend when user account is already ACTIVE (400)")
    void execute_accountAlreadyActive_throwsAccountAlreadyVerifiedException() {
        String email = "active@example.com";
        ResendVerificationRequest request = new ResendVerificationRequest(email);
        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hashed_pw");
        account.verifyEmail(); // Status = ACTIVE

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> resendVerificationEmailUseCase.execute(request))
                .isInstanceOf(AccountAlreadyVerifiedException.class)
                .hasMessage("Account is already verified.");

        verifyNoInteractions(otpService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("UTCID06 [A]: Propagate ResendCooldownException when 60s cooldown period is active (429)")
    void execute_cooldownActive_propagatesResendCooldownException() {
        String email = "cooldown@example.com";
        ResendVerificationRequest request = new ResendVerificationRequest(email);
        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hashed_pw");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.generateOtpForResend(email, OtpPurpose.EMAIL_VERIFICATION))
                .thenThrow(new ResendCooldownException("Please wait 60 seconds before requesting another email."));

        assertThatThrownBy(() -> resendVerificationEmailUseCase.execute(request))
                .isInstanceOf(ResendCooldownException.class)
                .hasMessage("Please wait 60 seconds before requesting another email.");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("UTCID07 [A]: Propagate MaxResendAttemptsException when maximum 3 resends exceeded (429)")
    void execute_maxAttemptsReached_propagatesMaxResendAttemptsException() {
        String email = "maxed@example.com";
        ResendVerificationRequest request = new ResendVerificationRequest(email);
        UserAccountEntity account = UserAccountEntity.createCustomerAccount(email, "hashed_pw");

        when(userAccountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(otpService.generateOtpForResend(email, OtpPurpose.EMAIL_VERIFICATION))
                .thenThrow(new MaxResendAttemptsException("Maximum resend attempts reached for this email."));

        assertThatThrownBy(() -> resendVerificationEmailUseCase.execute(request))
                .isInstanceOf(MaxResendAttemptsException.class)
                .hasMessage("Maximum resend attempts reached for this email.");

        verifyNoInteractions(eventPublisher);
    }
}
