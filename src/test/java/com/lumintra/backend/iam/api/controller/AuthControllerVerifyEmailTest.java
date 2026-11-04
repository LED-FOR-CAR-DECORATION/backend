package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.VerifyEmailRequest;
import com.lumintra.backend.iam.application.LoginUseCase;
import com.lumintra.backend.iam.application.RegisterAccountUseCase;
import com.lumintra.backend.iam.application.VerifyEmailUseCase;
import com.lumintra.backend.shared.exception.iam.AccountAlreadyVerifiedException;
import com.lumintra.backend.shared.exception.iam.InvalidOtpException;
import com.lumintra.backend.shared.exception.iam.MaxOtpAttemptsException;
import com.lumintra.backend.shared.exception.iam.UserNotFoundException;
import com.lumintra.backend.shared.response.ApiResponse;
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
class AuthControllerVerifyEmailTest {

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RegisterAccountUseCase registerAccountUseCase;

    @Mock
    private VerifyEmailUseCase verifyEmailUseCase;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("UTCID01 [N]: POST /api/auth/verify-email returns 200 OK with ApiResponse<String> envelope")
    void verifyEmail_successReturns200WithEnvelope() {
        VerifyEmailRequest request = new VerifyEmailRequest("customer@example.com", "123456");
        doNothing().when(verifyEmailUseCase).execute(request);

        ResponseEntity<ApiResponse<String>> response = authController.verifyEmail(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo("Email verified successfully");
        verify(verifyEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID02 [A]: POST /api/auth/verify-email propagates InvalidOtpException (400)")
    void verifyEmail_propagatesInvalidOtpException() {
        VerifyEmailRequest request = new VerifyEmailRequest("customer@example.com", "000000");
        doThrow(new InvalidOtpException("Invalid or expired OTP.")).when(verifyEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.verifyEmail(request))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired OTP.");
        verify(verifyEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/auth/verify-email propagates AccountAlreadyVerifiedException (400)")
    void verifyEmail_propagatesAccountAlreadyVerifiedException() {
        VerifyEmailRequest request = new VerifyEmailRequest("active@example.com", "123456");
        doThrow(new AccountAlreadyVerifiedException("Account is already verified")).when(verifyEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.verifyEmail(request))
                .isInstanceOf(AccountAlreadyVerifiedException.class)
                .hasMessage("Account is already verified");
        verify(verifyEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID04 [A]: POST /api/auth/verify-email propagates UserNotFoundException (404)")
    void verifyEmail_propagatesUserNotFoundException() {
        VerifyEmailRequest request = new VerifyEmailRequest("notfound@example.com", "123456");
        doThrow(new UserNotFoundException("Account not found")).when(verifyEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.verifyEmail(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Account not found");
        verify(verifyEmailUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID05 [A]: POST /api/auth/verify-email propagates MaxOtpAttemptsException (429)")
    void verifyEmail_propagatesMaxOtpAttemptsException() {
        VerifyEmailRequest request = new VerifyEmailRequest("customer@example.com", "999999");
        doThrow(new MaxOtpAttemptsException("Maximum OTP attempts reached. Please request a new code."))
                .when(verifyEmailUseCase).execute(request);

        assertThatThrownBy(() -> authController.verifyEmail(request))
                .isInstanceOf(MaxOtpAttemptsException.class)
                .hasMessage("Maximum OTP attempts reached. Please request a new code.");
        verify(verifyEmailUseCase).execute(request);
    }
}
