package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.ResendVerificationRequest;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterAccountUseCase;
import com.lumintra.backend.iam.application.ResendVerificationEmailUseCase;
import com.lumintra.backend.iam.application.VerifyEmailUseCase;
import com.lumintra.backend.shared.exception.iam.AccountAlreadyVerifiedException;
import com.lumintra.backend.shared.exception.iam.MaxResendAttemptsException;
import com.lumintra.backend.shared.exception.iam.ResendCooldownException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerResendVerificationTest {

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RegisterAccountUseCase registerAccountUseCase;

    @Mock
    private VerifyEmailUseCase verifyEmailUseCase;

    @Mock
    private ResendVerificationEmailUseCase resendVerificationEmailUseCase;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("UTCID01 [N]: POST /api/auth/resend-verification returns 202 ACCEPTED")
    void resendVerification_successReturns202Accepted() {
        ResendVerificationRequest request = new ResendVerificationRequest("customer@example.com");
        doNothing().when(resendVerificationEmailUseCase).execute(request);

        ResponseEntity<Void> response = authController.resendVerification(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
        verify(resendVerificationEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID02 [A]: POST /api/auth/resend-verification propagates ResendCooldownException (429)")
    void resendVerification_propagatesResendCooldownException() {
        ResendVerificationRequest request = new ResendVerificationRequest("customer@example.com");
        doThrow(new ResendCooldownException("Please wait 60 seconds before requesting another email."))
                .when(resendVerificationEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.resendVerification(request))
                .isInstanceOf(ResendCooldownException.class)
                .hasMessage("Please wait 60 seconds before requesting another email.");
        verify(resendVerificationEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/auth/resend-verification propagates MaxResendAttemptsException (429)")
    void resendVerification_propagatesMaxResendAttemptsException() {
        ResendVerificationRequest request = new ResendVerificationRequest("customer@example.com");
        doThrow(new MaxResendAttemptsException("Maximum resend attempts reached for this email."))
                .when(resendVerificationEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.resendVerification(request))
                .isInstanceOf(MaxResendAttemptsException.class)
                .hasMessage("Maximum resend attempts reached for this email.");
        verify(resendVerificationEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID04 [A]: POST /api/auth/resend-verification propagates AccountAlreadyVerifiedException (400)")
    void resendVerification_propagatesAccountAlreadyVerifiedException() {
        ResendVerificationRequest request = new ResendVerificationRequest("active@example.com");
        doThrow(new AccountAlreadyVerifiedException("Account is already verified."))
                .when(resendVerificationEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.resendVerification(request))
                .isInstanceOf(AccountAlreadyVerifiedException.class)
                .hasMessage("Account is already verified.");
        verify(resendVerificationEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID05 [A]: POST /api/auth/resend-verification propagates UserNotFoundException (404)")
    void resendVerification_propagatesUserNotFoundException() {
        ResendVerificationRequest request = new ResendVerificationRequest("notfound@example.com");
        doThrow(new UserNotFoundException("Account not found"))
                .when(resendVerificationEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.resendVerification(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Account not found");
        verify(resendVerificationEmailUseCase).execute(request);
    }
}
