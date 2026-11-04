package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.GoogleLoginRequest;
import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.application.GoogleLoginUseCase;
import com.lumintra.backend.shared.exception.iam.AccountLockedException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerGoogleLoginTest {

    private static Validator validator;

    @Mock
    private GoogleLoginUseCase googleLoginUseCase;

    @InjectMocks
    private AuthController authController;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("UTCID01 [N]: POST /api/auth/google returns 200 OK with ApiResponse<LoginResponse> envelope")
    void googleLogin_successReturns200WithEnvelope() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid.google.id.token");
        LoginResponse responsePayload = LoginResponse.of("access-token-abc", "refresh-token-xyz");

        when(googleLoginUseCase.execute(request)).thenReturn(responsePayload);

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.googleLogin(request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getData()).isEqualTo(responsePayload);
        verify(googleLoginUseCase).execute(request);
    }

    @Test
    @DisplayName("UTCID02 [A]: POST /api/auth/google propagates InvalidCredentialsException on invalid token (401)")
    void googleLogin_propagatesInvalidCredentialsException_invalidToken() {
        GoogleLoginRequest request = new GoogleLoginRequest("invalid.token");

        when(googleLoginUseCase.execute(request))
                .thenThrow(new InvalidCredentialsException("Failed to verify Google token."));

        assertThatThrownBy(() -> authController.googleLogin(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Failed to verify Google token.");
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/auth/google propagates InvalidCredentialsException on staff account rejection (401)")
    void googleLogin_propagatesInvalidCredentialsException_staffAccount() {
        GoogleLoginRequest request = new GoogleLoginRequest("staff.token");

        when(googleLoginUseCase.execute(request))
                .thenThrow(new InvalidCredentialsException("Google login is not supported for staff accounts."));

        assertThatThrownBy(() -> authController.googleLogin(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Google login is not supported for staff accounts.");
    }

    @Test
    @DisplayName("UTCID04 [A]: POST /api/auth/google propagates InvalidCredentialsException on inactive account (401)")
    void googleLogin_propagatesInvalidCredentialsException_inactiveAccount() {
        GoogleLoginRequest request = new GoogleLoginRequest("suspended.token");

        when(googleLoginUseCase.execute(request))
                .thenThrow(new InvalidCredentialsException("Account is suspended or deleted."));

        assertThatThrownBy(() -> authController.googleLogin(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Account is suspended or deleted.");
    }

    @Test
    @DisplayName("UTCID05 [A]: POST /api/auth/google propagates AccountLockedException (423)")
    void googleLogin_propagatesAccountLockedException() {
        GoogleLoginRequest request = new GoogleLoginRequest("locked.token");

        when(googleLoginUseCase.execute(request))
                .thenThrow(new AccountLockedException("Account is locked for 15 minutes."));

        assertThatThrownBy(() -> authController.googleLogin(request))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage("Account is locked for 15 minutes.");
    }

    @Test
    @DisplayName("UTCID06 [A]: Validation fails when GoogleLoginRequest idToken is blank")
    void googleLogin_validationFailsWhenIdTokenBlank() {
        GoogleLoginRequest request = new GoogleLoginRequest("   ");

        Set<ConstraintViolation<GoogleLoginRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().equals("Google ID token is required"));
    }
}
